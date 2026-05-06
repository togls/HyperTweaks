package io.github.togls.hypertweaks.xposed.hook

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.data.KeepAlivePackages
import io.github.togls.hypertweaks.data.RemotePreferenceKeys
import io.github.togls.hypertweaks.xposed.util.HookLog
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

class KeepAliveHook(
    private val module: XposedModule,
) {

    private val keepAlivePackages = AtomicReference<Set<String>>(emptySet())

    private val preferenceListeners =
        mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val hookedMethods = Collections.newSetFromMap(
        IdentityHashMap<Method, Boolean>(),
    )

    fun installSystemServer(classLoader: ClassLoader) {
        loadRemotePreferences()

        hookActivityManagerService(classLoader)
        hookProcessRecord(classLoader)
        hookProcessList(classLoader)
        hookMiuiSmartPowerService(classLoader)
    }

    private fun hookActivityManagerService(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ActivityManagerService",
        ) ?: return

        val methodNames = setOf(
            "killBackgroundProcesses",
            "killBackgroundProcessesWithFeature",
            "killPackageDependents",
            "forceStopPackage",
            "forceStopPackageAsUser",
            "forceStopPackageLocked",
        )

        targetClass.declaredMethods
            .filter { method -> method.name in methodNames }
            .forEach { method ->
                hookMethodWithPackageArgs(method)
            }

        HookLog.i(module, "KeepAliveHook installed for ActivityManagerService")
    }

    private fun hookProcessRecord(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ProcessRecord",
        ) ?: return

        targetClass.declaredMethods
            .filter { method ->
                method.name == "kill" || method.name == "killLocked"
            }
            .forEach { method ->
                hookProcessRecordKillMethod(method)
            }

        HookLog.i(module, "KeepAliveHook installed for ProcessRecord")
    }

    private fun hookProcessList(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ProcessList",
        ) ?: return

        hookCleanerClass(
            targetClass = targetClass,
            keywords = PROCESS_LIST_CLEANUP_KEYWORDS,
            label = "ProcessList",
        )
    }

    private fun hookCleanerClass(
        targetClass: Class<*>,
        keywords: Set<String>,
        label: String,
    ) {
        val candidateMethods = targetClass.declaredMethods
            .filter { method ->
                method.matchesAnyKeyword(keywords) &&
                    method.hasSupportedPackageParameter()
            }

        candidateMethods.forEach { method ->
            hookMethodWithPackageArgs(method)
        }

        HookLog.i(
            module,
            "KeepAliveHook installed for $label: candidates=${candidateMethods.size}",
        )
    }

    private fun hookMiuiSmartPowerService(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.miui.server.smartpower.SmartPowerService",
        ) ?: return

        hookMiuiCleanerClass(
            targetClass = targetClass,
            keywords = listOf(
                "kill",
                "clean",
                "trim",
                "hibernate",
                "idle",
                "power",
                "freeze",
            ),
        )

        HookLog.i(module, "KeepAliveHook installed for SmartPowerService")
    }

    private fun hookMiuiCleanerClass(
        targetClass: Class<*>,
        keywords: List<String>,
    ) {
        targetClass.declaredMethods
            .filter { method ->
                keywords.any { keyword ->
                    method.name.contains(keyword, ignoreCase = true)
                }
            }
            .forEach { method ->
                hookMethodWithPackageArgs(method)
            }
    }

    private fun hookMethodWithPackageArgs(method: Method) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val protectedPackage = findProtectedPackageFromArgs(chain.args)

                    if (protectedPackage != null) {
                        HookLog.i(
                            module,
                            "blocked package cleanup: ${method.describeSignature()} package=$protectedPackage",
                        )

                        return@intercept defaultReturnValue(method.returnType)
                    }

                    chain.proceed()
                }

            HookLog.i(
                module,
                "hooked keep-alive method: ${method.describeSignature()}",
            )
        }.onFailure { error ->
            HookLog.w(
                module,
                "failed to hook keep-alive method: ${method.describeSignature()}",
                error,
            )
        }
    }

    private fun hookProcessRecordKillMethod(method: Method) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val processRecord = chain.thisObject

                    val protectedPackage = findProtectedPackageFromArgs(chain.args)
                        ?: findProtectedPackageFromProcessRecord(processRecord)

                    if (protectedPackage != null) {
                        HookLog.i(
                            module,
                            "blocked process kill: ${method.declaringClass.name}#${method.name} package=$protectedPackage",
                        )

                        return@intercept defaultReturnValue(method.returnType)
                    }

                    chain.proceed()
                }

            HookLog.i(
                module,
                "hooked process kill method: ${method.declaringClass.name}#${method.name}",
            )
        }.onFailure { error ->
            HookLog.w(
                module,
                "failed to hook process kill method: ${method.declaringClass.name}#${method.name}",
                error,
            )
        }
    }

    private fun rememberHookedMethod(method: Method): Boolean {
        synchronized(hookedMethods) {
            return hookedMethods.add(method)
        }
    }

    private fun findProtectedPackageFromArgs(args: List<Any?>): String? {
        val protectedPackages = keepAlivePackages.get()

        if (protectedPackages.isEmpty()) {
            return null
        }

        return args
            .flatMap { value -> extractStrings(value) }
            .firstOrNull { value -> isProtectedName(value, protectedPackages) }
            ?.let { value -> normalizeProtectedName(value, protectedPackages) }
    }

    private fun findProtectedPackageFromProcessRecord(processRecord: Any?): String? {
        if (processRecord == null) {
            return null
        }

        val protectedPackages = keepAlivePackages.get()

        if (protectedPackages.isEmpty()) {
            return null
        }

        val candidates = buildList {
            addAll(readProcessNameCandidates(processRecord))
            addAll(readApplicationInfoCandidates(processRecord))
            addAll(readPackageListCandidates(processRecord))
        }

        return candidates
            .firstOrNull { value -> isProtectedName(value, protectedPackages) }
            ?.let { value -> normalizeProtectedName(value, protectedPackages) }
    }

    private fun readProcessNameCandidates(processRecord: Any): List<String> {
        return listOf(
            "processName",
            "mProcessName",
        ).mapNotNull { fieldName ->
            readFieldValue(processRecord, fieldName) as? String
        }
    }

    private fun readApplicationInfoCandidates(processRecord: Any): List<String> {
        return listOf(
            "info",
            "mInfo",
        ).mapNotNull { fieldName ->
            readFieldValue(processRecord, fieldName) as? ApplicationInfo
        }.mapNotNull { appInfo ->
            appInfo.packageName
        }
    }

    private fun readPackageListCandidates(processRecord: Any): List<String> {
        val pkgList = listOf(
            "pkgList",
            "mPkgList",
        ).firstNotNullOfOrNull { fieldName ->
            readFieldValue(processRecord, fieldName)
        } ?: return emptyList()

        return buildList {
            addAll(extractStrings(pkgList))

            val packageList = invokeNoArgMethod(
                receiver = pkgList,
                methodName = "getPackageList",
            )

            addAll(extractStrings(packageList))

            val packageMap = invokeNoArgMethod(
                receiver = pkgList,
                methodName = "getPackages",
            )

            addAll(extractStrings(packageMap))
        }
    }

    private fun extractStrings(value: Any?): List<String> {
        return extractStrings(
            value = value,
            depth = 0,
            visited = Collections.newSetFromMap(IdentityHashMap()),
        )
    }

    private fun extractStrings(
        value: Any?,
        depth: Int,
        visited: MutableSet<Any>,
    ): List<String> {
        if (value == null || depth > MAX_EXTRACT_DEPTH) {
            return emptyList()
        }

        if (!visited.add(value)) {
            return emptyList()
        }

        return when (value) {
            is String -> listOf(value)

            is Array<*> -> value.flatMap { item ->
                extractStrings(
                    value = item,
                    depth = depth + 1,
                    visited = visited,
                )
            }

            is Iterable<*> -> value.flatMap { item ->
                extractStrings(
                    value = item,
                    depth = depth + 1,
                    visited = visited,
                )
            }

            is Map<*, *> -> {
                val keys = value.keys.flatMap { key ->
                    extractStrings(
                        value = key,
                        depth = depth + 1,
                        visited = visited,
                    )
                }

                val values = value.values.flatMap { mapValue ->
                    extractStrings(
                        value = mapValue,
                        depth = depth + 1,
                        visited = visited,
                    )
                }

                keys + values
            }

            is ApplicationInfo -> listOfNotNull(value.packageName)

            else -> emptyList()
        }
    }

    private fun isProtectedName(
        value: String,
        protectedPackages: Set<String>,
    ): Boolean {
        return protectedPackages.any { packageName ->
            value == packageName || value.startsWith("$packageName:")
        }
    }

    private fun normalizeProtectedName(
        value: String,
        protectedPackages: Set<String>,
    ): String {
        return protectedPackages.firstOrNull { packageName ->
            value == packageName || value.startsWith("$packageName:")
        } ?: value
    }

    private fun readFieldValue(
        receiver: Any,
        fieldName: String,
    ): Any? {
        val field = receiver.javaClass.findField(fieldName) ?: return null

        return runCatching {
            field.get(receiver)
        }.getOrNull()
    }

    private fun invokeNoArgMethod(
        receiver: Any,
        methodName: String,
    ): Any? {
        val method = receiver.javaClass.findNoArgMethod(methodName) ?: return null

        return runCatching {
            method.invoke(receiver)
        }.getOrNull()
    }

    private fun Class<*>.findField(fieldName: String): Field? {
        var current: Class<*>? = this

        while (current != null) {
            val field = runCatching {
                current.getDeclaredField(fieldName).apply {
                    isAccessible = true
                }
            }.getOrNull()

            if (field != null) {
                return field
            }

            current = current.superclass
        }

        return null
    }

    private fun Class<*>.findNoArgMethod(methodName: String): Method? {
        var current: Class<*>? = this

        while (current != null) {
            val method = current.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName && candidate.parameterTypes.isEmpty()
            }

            if (method != null) {
                method.isAccessible = true
                return method
            }

            current = current.superclass
        }

        return null
    }

    @SuppressLint("PrivateApi")
    private fun loadClassOrNull(
        classLoader: ClassLoader,
        className: String,
    ): Class<*>? {
        return runCatching {
            classLoader.loadClass(className)
        }.onFailure {
            HookLog.i(module, "skip optional KeepAliveHook target: $className not found")
        }.getOrNull()
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? {
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> null
            else -> null
        }
    }

    private fun loadRemotePreferences() {
        runCatching {
            val prefs = module.getRemotePreferences(RemotePreferenceKeys.GroupName)

            updateKeepAlivePackages(prefs)

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key != RemotePreferenceKeys.KeepAlivePackages) {
                    return@OnSharedPreferenceChangeListener
                }

                updateKeepAlivePackages(sharedPreferences)
            }

            preferenceListeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { error ->
            HookLog.w(module, "failed to read keep-alive remote preferences", error)
        }
    }

    private fun updateKeepAlivePackages(prefs: SharedPreferences) {
        val raw = prefs.getString(
            RemotePreferenceKeys.KeepAlivePackages,
            "",
        ).orEmpty()

        val packages = KeepAlivePackages.parse(raw)

        keepAlivePackages.set(packages)

        HookLog.i(
            module,
            "keep-alive packages updated: ${packages.joinToString()}",
        )
    }

    private companion object {
        private const val MAX_EXTRACT_DEPTH = 3

        private val PROCESS_LIST_CLEANUP_KEYWORDS = setOf(
            "kill",
        )

        private val MIUI_PROCESS_MANAGER_CLEANUP_KEYWORDS = setOf(
            "kill",
            "clean",
            "trim",
            "forceStop",
        )

        private val MIUI_SMART_POWER_CLEANUP_KEYWORDS = setOf(
            "kill",
            "clean",
            "trim",
            "hibernate",
            "idle",
            "freeze",
        )
    }

    private fun Method.matchesAnyKeyword(keywords: Set<String>): Boolean {
        return keywords.any { keyword ->
            name.contains(keyword, ignoreCase = true)
        }
    }

    private fun Method.hasSupportedPackageParameter(): Boolean {
        return parameterTypes.any { parameterType ->
            parameterType == String::class.java ||
                parameterType.isArray ||
                Iterable::class.java.isAssignableFrom(parameterType) ||
                Map::class.java.isAssignableFrom(parameterType) ||
                ApplicationInfo::class.java.isAssignableFrom(parameterType)
        }
    }

    private fun Method.describeSignature(): String {
        val params = parameterTypes.joinToString(", ") { parameterType ->
            parameterType.name
        }

        return "${declaringClass.name}#$name($params): ${returnType.name}"
    }
}