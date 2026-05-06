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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class OomAdjProtectHook(
    private val module: XposedModule,
) {

    private val keepAlivePackages = AtomicReference<Set<String>>(emptySet())

    private val pidToPackage = ConcurrentHashMap<Int, String>()

    private val protectedProcesses = ConcurrentHashMap<Int, ProtectedProcess>()

    private data class ProtectedProcess(
        val pid: Int,
        val packageName: String,
        val processName: String?,
    )

    private val preferenceListeners =
        mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val hookedMethods = Collections.newSetFromMap(
        IdentityHashMap<Method, Boolean>(),
    )

    fun installSystemServer(classLoader: ClassLoader) {
        loadRemotePreferences()

        hookProcessRecord(classLoader)
        hookProcessListSetOomAdj(classLoader)
    }

    private fun hookProcessRecord(classLoader: ClassLoader) {
        val processRecordClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ProcessRecord",
        ) ?: return

        processRecordClass.declaredMethods
            .filter { method ->
                method.name == "setPid" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            .forEach { method ->
                hookProcessRecordSetPid(method)
            }

        HookLog.i(module, "OomAdjProtectHook installed for ProcessRecord")
    }

    private fun hookProcessRecordSetPid(method: Method) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val processRecord = chain.thisObject
                    val oldPid = processRecord?.let { readPidFromProcessRecord(it) }

                    val result = chain.proceed()

                    val newPid = chain.args.firstOrNull() as? Int

                    if (oldPid != null && oldPid > 0 && oldPid != newPid) {
                        forgetProtectedProcess(
                            pid = oldPid,
                            reason = "pid changed",
                        )
                    }

                    if (processRecord == null || newPid == null || newPid <= 0) {
                        if (oldPid != null && oldPid > 0) {
                            forgetProtectedProcess(
                                pid = oldPid,
                                reason = "process pid cleared",
                            )
                        }

                        return@intercept result
                    }

                    val protectedPackage = findProtectedPackageFromProcessRecord(processRecord)

                    if (protectedPackage == null) {
                        forgetProtectedProcess(
                            pid = newPid,
                            reason = "process is not protected",
                        )

                        return@intercept result
                    }

                    val protectedProcess = ProtectedProcess(
                        pid = newPid,
                        packageName = protectedPackage,
                        processName = readPrimaryProcessName(processRecord),
                    )

                    rememberProtectedProcess(protectedProcess)
                    applyProtectedMaxAdj(processRecord)
                    writeOomScoreAdj(newPid, PROTECTED_OOM_ADJ)

                    HookLog.i(
                        module,
                        "tracked protected process: pid=$newPid package=$protectedPackage process=${protectedProcess.processName.orEmpty()}",
                    )

                    result
                }

            HookLog.i(module, "hooked ProcessRecord#setPid(int)")
        }.onFailure { error ->
            HookLog.w(module, "failed to hook ProcessRecord#setPid", error)
        }
    }

    private fun writeOomScoreAdj(
        pid: Int,
        adj: Int,
    ) {
        runCatching {
            java.io.File("/proc/$pid/oom_score_adj").writeText(adj.toString())
            HookLog.i(module, "write oom_score_adj: pid=$pid adj=$adj")
        }.onFailure { error ->
            HookLog.w(module, "failed to write oom_score_adj: pid=$pid adj=$adj", error)
        }
    }

    private fun hookProcessListSetOomAdj(classLoader: ClassLoader) {
        val processListClass = loadClassOrNull(
            classLoader = classLoader,
            className = "com.android.server.am.ProcessList",
        ) ?: return

        val methods = processListClass.declaredMethods
            .filter { method ->
                method.isSupportedSetOomAdjSignature()
            }

        if (methods.isEmpty()) {
            val candidates = processListClass.declaredMethods
                .filter { method -> method.name == "setOomAdj" }
                .joinToString(separator = "\n") { method ->
                    method.describeSignature()
                }

            HookLog.i(
                module,
                "skip ProcessList#setOomAdj: no supported signature, candidates=$candidates",
            )

            return
        }

        methods.forEach { method ->
            hookSetOomAdj(method)
        }

        HookLog.i(
            module,
            "OomAdjProtectHook installed for ProcessList#setOomAdj: count=${methods.size}",
        )
    }

    private fun hookSetOomAdj(method: Method) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = chain.args

                    val pid = args.getOrNull(0) as? Int
                    val adj = args.getOrNull(2) as? Int

                    if (pid == null || adj == null) {
                        return@intercept chain.proceed()
                    }

                    val protectedProcess =
                        protectedProcesses[pid] ?: return@intercept chain.proceed()

                    if (!isStillSameProtectedProcess(protectedProcess)) {
                        forgetProtectedProcess(
                            pid = pid,
                            reason = "pid no longer belongs to protected package",
                        )

                        return@intercept chain.proceed()
                    }

                    if (adj <= PROTECTED_OOM_ADJ) {
                        return@intercept chain.proceed()
                    }

                    val newArgs = args.toTypedArray()
                    newArgs[2] = PROTECTED_OOM_ADJ

                    HookLog.i(
                        module,
                        "clamp oom adj: pid=$pid package=${protectedProcess.packageName} $adj->$PROTECTED_OOM_ADJ",
                    )

                    chain.proceed(newArgs)
                }

            HookLog.i(
                module,
                "hooked ProcessList#setOomAdj: ${method.parameterTypes.joinToString()}",
            )
        }.onFailure { error ->
            HookLog.w(module, "failed to hook ProcessList#setOomAdj", error)
        }
    }

    private fun applyProtectedMaxAdj(processRecord: Any) {
        runCatching {
            val state = readFieldValue(processRecord, "mState")
                ?: readFieldValue(processRecord, "state")
                ?: return

            listOf(
                "setMaxAdj",
                "setCurAdj",
                "setCurRawAdj",
                "setSetAdj",
                "setSetRawAdj",
            ).forEach { methodName ->
                invokeIntMethodIfExists(
                    receiver = state,
                    methodName = methodName,
                    value = PROTECTED_OOM_ADJ,
                )
            }
        }.onFailure { error ->
            HookLog.w(module, "failed to apply protected max adj", error)
        }
    }

    private fun rememberProtectedProcess(process: ProtectedProcess) {
        protectedProcesses[process.pid] = process
    }

    private fun forgetProtectedProcess(
        pid: Int,
        reason: String,
    ) {
        val removed = protectedProcesses.remove(pid) ?: return

        HookLog.i(
            module,
            "forgot protected process: pid=$pid package=${removed.packageName} reason=$reason",
        )
    }

    private fun isStillSameProtectedProcess(process: ProtectedProcess): Boolean {
        val cmdline = readProcCmdline(process.pid) ?: return false

        return cmdline == process.packageName ||
            cmdline == process.processName ||
            cmdline.startsWith("${process.packageName}:") ||
            process.processName?.let { processName ->
                cmdline.startsWith("$processName:")
            } == true
    }

    private fun readProcCmdline(pid: Int): String? {
        return runCatching {
            val bytes = java.io.File("/proc/$pid/cmdline").readBytes()
            val cmdlineBytes = bytes.takeWhile { byte -> byte.toInt() != 0 }.toByteArray()

            cmdlineBytes
                .toString(Charsets.UTF_8)
                .trim()
                .takeIf { value -> value.isNotEmpty() }
        }.getOrNull()
    }

    private fun readPidFromProcessRecord(processRecord: Any): Int? {
        return listOf(
            "mPid",
            "pid",
        ).firstNotNullOfOrNull { fieldName ->
            readFieldValue(processRecord, fieldName) as? Int
        }
    }

    private fun readPrimaryProcessName(processRecord: Any): String? {
        return readProcessNameCandidates(processRecord).firstOrNull()
    }

    private fun findProtectedPackageFromProcessRecord(processRecord: Any): String? {
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

            addAll(
                extractStrings(
                    invokeNoArgMethod(
                        receiver = pkgList,
                        methodName = "getPackageList",
                    ),
                ),
            )

            addAll(
                extractStrings(
                    invokeNoArgMethod(
                        receiver = pkgList,
                        methodName = "getPackages",
                    ),
                ),
            )
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
                extractStrings(item, depth + 1, visited)
            }

            is Iterable<*> -> value.flatMap { item ->
                extractStrings(item, depth + 1, visited)
            }

            is Map<*, *> -> {
                value.keys.flatMap { key ->
                    extractStrings(key, depth + 1, visited)
                } + value.values.flatMap { mapValue ->
                    extractStrings(mapValue, depth + 1, visited)
                }
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

    private fun invokeIntMethodIfExists(
        receiver: Any,
        methodName: String,
        value: Int,
    ) {
        val method = receiver.javaClass.findIntMethod(methodName) ?: return

        runCatching {
            method.invoke(receiver, value)
        }.onFailure { error ->
            HookLog.w(module, "failed to invoke ${receiver.javaClass.name}#$methodName", error)
        }
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

    private fun Class<*>.findIntMethod(methodName: String): Method? {
        var current: Class<*>? = this
        val intType = Int::class.javaPrimitiveType

        while (current != null) {
            val method = current.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == intType
            }

            if (method != null) {
                method.isAccessible = true
                return method
            }

            current = current.superclass
        }

        return null
    }

    private fun rememberHookedMethod(method: Method): Boolean {
        synchronized(hookedMethods) {
            return hookedMethods.add(method)
        }
    }

    @SuppressLint("PrivateApi")
    private fun loadClassOrNull(
        classLoader: ClassLoader,
        className: String,
    ): Class<*>? {
        return runCatching {
            classLoader.loadClass(className)
        }.onFailure { error ->
            HookLog.w(module, "skip OomAdjProtectHook: $className not found", error)
        }.getOrNull()
    }

    private fun loadRemotePreferences() {
        runCatching {
            val prefs = module.getRemotePreferences(RemotePreferenceKeys.GroupName)

            updateKeepAlivePackages(prefs)

            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key != RemotePreferenceKeys.KeepAlivePackages) {
                        return@OnSharedPreferenceChangeListener
                    }

                    updateKeepAlivePackages(sharedPreferences)
                }

            preferenceListeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { error ->
            HookLog.w(module, "failed to read oom-adj remote preferences", error)
        }
    }

    private fun updateKeepAlivePackages(prefs: SharedPreferences) {
        val raw = prefs.getString(
            RemotePreferenceKeys.KeepAlivePackages,
            "",
        ).orEmpty()

        val packages = KeepAlivePackages.parse(raw)

        keepAlivePackages.set(packages)

        protectedProcesses.forEach { (pid, process) ->
            if (process.packageName !in packages) {
                forgetProtectedProcess(
                    pid = pid,
                    reason = "package removed from keep-alive list",
                )
            }
        }

        HookLog.i(
            module,
            "oom-adj protected packages updated: ${packages.joinToString()}",
        )
    }

    private fun Method.describeSignature(): String {
        val params = parameterTypes.joinToString(", ") { type -> type.name }
        return "${declaringClass.name}#$name($params): ${returnType.name}"
    }

    private fun Method.isSupportedSetOomAdjSignature(): Boolean {
        if (!isSupportedOomAdjSdk()) {
            return false
        }

        val intType = Int::class.javaPrimitiveType

        return name == "setOomAdj" &&
            parameterTypes.contentEquals(
                arrayOf(
                    intType,
                    intType,
                    intType,
                ),
            )
    }

    private fun isSupportedOomAdjSdk(): Boolean {
        // return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        return true
    }

    private companion object {
        private const val MAX_EXTRACT_DEPTH = 3

        /**
         * 200 roughly corresponds to a perceptible-level process.
         *
         * If Firefox still reloads, temporarily try 0 for stronger foreground-level protection,
         * but that is more aggressive and may increase memory pressure.
         */
        private const val PROTECTED_OOM_ADJ = 200
    }
}