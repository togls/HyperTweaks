package io.github.togls.hypertweaks.feature.keepalive.xposed

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

private const val GROUP_AMS_BACKGROUND = "AMS_BACKGROUND"
private const val GROUP_AMS_AGGRESSIVE = "AMS_AGGRESSIVE"
private const val GROUP_PROCESS_LIST_CLEANUP = "PROCESS_LIST_CLEANUP"
private const val GROUP_PROCESS_LIST_REMOVE = "PROCESS_LIST_REMOVE"
private const val GROUP_PROCESS_RECORD_KILL = "PROCESS_RECORD_KILL"
private const val GROUP_MIUI_PROCESS_MANAGER = "MIUI_PROCESS_MANAGER_SERVICE"
private const val GROUP_MIUI_SMART_POWER = "MIUI_SMART_POWER"

private val CONSERVATIVE_BLOCK_GROUPS = setOf(
    GROUP_AMS_BACKGROUND,
    GROUP_PROCESS_LIST_CLEANUP,
    GROUP_MIUI_PROCESS_MANAGER,
    GROUP_MIUI_SMART_POWER,
)

class KeepAliveHook(
    context: HookContext
) {

    private val module = context.module
    private val log = context.log

    private val keepAlivePackages = AtomicReference<Set<String>>(emptySet())

    private val preferenceListeners =
        mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val hookedMethods = Collections.newSetFromMap(
        IdentityHashMap<Method, Boolean>(),
    )

    private val keepAliveMode = AtomicReference(KeepAliveMode.Default)

    private companion object {
        private const val MAX_EXTRACT_DEPTH = 3

        private val PROCESS_LIST_CLEANUP_KEYWORDS = setOf(
            "kill",
            "remove",
        )

        private val MIUI_SMART_POWER_CLEANUP_KEYWORDS = setOf(
            "kill",
            "clean",
            "trim",
            "hibernate",
            "idle",
            "power",
            "freeze",
        )

        private val AMS_BACKGROUND_CLEANUP_METHOD_NAMES = setOf(
            "killBackgroundProcesses",
            "killBackgroundProcessesWithFeature",
            "killPackageDependents",
        )

        private val AMS_AGGRESSIVE_METHOD_NAMES = setOf(
            "forceStopPackage",
            "forceStopPackageAsUser",
            "forceStopPackageLocked",
        )
    }

    fun installSystemServer(classLoader: ClassLoader) {
        loadRemotePreferences()

        hookActivityManagerService(classLoader)

        hookProcessList(classLoader)
        hookProcessRecord(classLoader)

        // if (!RomUtils.isXiaomiLikeRom()) {
        //    log.i( "skip MIUI/HyperOS keep-alive hooks on non-Xiaomi ROM")
        //    return
        // }
        hookMiuiProcessManagerService(classLoader)

        hookMiuiSmartPowerService(classLoader)
    }

    private fun hookMiuiProcessManagerService(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.miui.server.process.ProcessManagerService",
        ) ?: return

        hookMiuiCleanerClass(
            targetClass = targetClass,
            keywords = listOf(
                "kill",
                "clean",
                "trim",
                "forceStop",
                "remove",
            ),
            group = GROUP_MIUI_PROCESS_MANAGER,
        )
    }

    private fun hookActivityManagerService(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ActivityManagerService",
        ) ?: return

        targetClass.declaredMethods
            .filter { method -> method.name in AMS_BACKGROUND_CLEANUP_METHOD_NAMES }
            .forEach { method ->
                hookMethodWithPackageArgs(method, GROUP_AMS_BACKGROUND)
            }

        targetClass.declaredMethods
            .filter { method -> method.name in AMS_AGGRESSIVE_METHOD_NAMES }
            .forEach { method ->
                hookMethodWithPackageArgs(method, GROUP_AMS_AGGRESSIVE)
            }

        log.i("KeepAliveHook installed for ActivityManagerService")
    }

    private fun hookProcessRecord(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ProcessRecord",
        ) ?: return

        targetClass.declaredMethods
            .filter { method ->
                method.name == "kill" || method.name == "killLocked"
                // method.isSupportedProcessRecordKillSignature()
            }
            .forEach { method ->
                hookProcessRecordKillMethod(method)
            }

        log.i("KeepAliveHook installed for ProcessRecord")
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
            .filter { method -> method.matchesAnyKeyword(keywords) }

        candidateMethods.forEach { method ->
            val group = if (method.name.contains("remove", ignoreCase = true)) {
                GROUP_PROCESS_LIST_REMOVE
            } else {
                GROUP_PROCESS_LIST_CLEANUP
            }

            hookMethodWithPackageArgs(method, group)
        }
    }

    private fun hookMiuiSmartPowerService(classLoader: ClassLoader) {
        val targetClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.miui.server.smartpower.SmartPowerService",
        ) ?: return

        hookMiuiCleanerClass(
            targetClass = targetClass,
            keywords = MIUI_SMART_POWER_CLEANUP_KEYWORDS.toList(),
            group = GROUP_MIUI_SMART_POWER,
        )
    }

    private fun hookMiuiCleanerClass(
        targetClass: Class<*>,
        keywords: List<String>,
        group: String,
    ) {
        targetClass.declaredMethods
            .filter { method ->
                keywords.any { keyword ->
                    method.name.contains(keyword, ignoreCase = true)
                }
            }
            .forEach { method ->
                hookMethodWithPackageArgs(method, group)
            }
    }

    private fun hookMethodWithPackageArgs(method: Method, group: String) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val protectedPackage = findProtectedPackageFromArgs(chain.args)

                    if (
                        protectedPackage != null &&
                        shouldBlockKeepAliveCall(group)
                    ) {
                        log.i(
                            "blocked keep-alive: group=$group" +
                                " method=${method.describeSignature()}" +
                                " package=$protectedPackage" +
                                " mode=${keepAliveMode.get()}",
                        )

                        return@intercept defaultReturnValue(method.returnType)
                    }

                    chain.proceed()
                }

            log.i(
                "hooked keep-alive method: ${method.describeSignature()}",
            )
        }.onFailure { error ->
            log.w(
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

                    if (
                        protectedPackage != null &&
                        shouldBlockKeepAliveCall(GROUP_PROCESS_RECORD_KILL)
                    ) {
                        log.i(
                            "blocked process kill: group=$GROUP_PROCESS_RECORD_KILL " +
                                "${method.describeSignature()} package=$protectedPackage " +
                                "mode=${keepAliveMode.get()}",
                        )
                        return@intercept defaultReturnValue(method.returnType)
                    }

                    chain.proceed()
                }

            log.i(
                "hooked process kill method: ${method.describeSignature()}",
            )
        }.onFailure { error ->
            log.w(
                "failed to hook process kill method: ${method.describeSignature()}",
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
            log.i("skip optional KeepAliveHook target: $className not found")
        }.getOrNull()
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? {
        return when (returnType) {
            Void.TYPE -> null
            Boolean::class.javaPrimitiveType -> false
            Byte::class.javaPrimitiveType -> 0.toByte()
            Short::class.javaPrimitiveType -> 0.toShort()
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Char::class.javaPrimitiveType -> '\u0000'
            else -> null
        }
    }

    private fun loadRemotePreferences() {
        runCatching {
            val prefs = module.getRemotePreferences(RemotePreferenceKeys.GroupName)

            updateKeepAliveConfig(prefs)

            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    when (key) {
                        RemotePreferenceKeys.KeepAlivePackages,
                        RemotePreferenceKeys.KeepAliveMode,
                            -> updateKeepAliveConfig(sharedPreferences)
                    }
                }

            preferenceListeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { error ->
            log.w("failed to read keep-alive remote preferences", error)
        }
    }

    private fun updateKeepAliveConfig(prefs: SharedPreferences) {
        updateKeepAlivePackages(prefs)
        updateKeepAliveMode(prefs)
    }

    private fun updateKeepAliveMode(prefs: SharedPreferences) {
        val mode = KeepAliveMode.fromValue(
            prefs.getString(
                RemotePreferenceKeys.KeepAliveMode,
                KeepAliveMode.Default.value,
            ),
        )

        keepAliveMode.set(mode)

        log.i(
            "keep-alive mode updated: $mode",
        )
    }

    private fun updateKeepAlivePackages(prefs: SharedPreferences) {
        val raw = prefs.getString(
            RemotePreferenceKeys.KeepAlivePackages,
            "",
        ).orEmpty()

        val packages = KeepAlivePackages.parse(raw)

        keepAlivePackages.set(packages)

        log.i(
            "keep-alive packages updated: ${packages.joinToString()}",
        )
    }

    private fun shouldBlockKeepAliveCall(group: String): Boolean {
        return when (keepAliveMode.get()) {
            KeepAliveMode.OomOnly -> false

            KeepAliveMode.Conservative -> {
                group in CONSERVATIVE_BLOCK_GROUPS
            }

            KeepAliveMode.Aggressive -> true
        }
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

    private fun Method.isSupportedProcessRecordKillSignature(): Boolean {
        if (name != "kill" && name != "killLocked") {
            return false
        }

        val params = parameterTypes

        return params.contentEquals(
            arrayOf(
                String::class.java,
                Boolean::class.javaPrimitiveType,
            ),
        ) || params.contentEquals(
            arrayOf(
                String::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ),
        )
    }
}