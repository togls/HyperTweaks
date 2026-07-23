package io.github.togls.hypertweaks.feature.keepalive.xposed

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.core.xposed.snapshotOrDisabled
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class OomAdjProtectHook(
    context: HookContext
) {
    
    private val engine = context.engine
    private val log = context.log
    private val initialSettings = context.settings
    private val settingsProvider = context.settingsProvider

    private val keepAlivePackages = AtomicReference<Set<String>>(emptySet())

    private val pidToPackage = ConcurrentHashMap<Int, String>()

    private val protectedProcesses = ConcurrentHashMap<Int, ProtectedProcess>()

    private data class ProtectedProcess(
        val pid: Int,
        val packageName: String,
        val processName: String?,
    )

    private val settingsSubscriptions = mutableListOf<HookSettingsSubscription>()

    private val hookedMethods = Collections.newSetFromMap(
        IdentityHashMap<Method, Boolean>(),
    )

    fun installSystemServer(classLoader: ClassLoader) {
        observeSettings()

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
    }

    private fun hookProcessRecordSetPid(method: Method) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            engine.hook(method) { chain ->
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

                        return@hook result
                    }

                    val protectedPackage = findProtectedPackageFromProcessRecord(processRecord)

                    if (protectedPackage == null) {
                        forgetProtectedProcess(
                            pid = newPid,
                            reason = "process is not protected",
                        )

                        return@hook result
                    }

                    val protectedProcess = ProtectedProcess(
                        pid = newPid,
                        packageName = protectedPackage,
                        processName = readPrimaryProcessName(processRecord),
                    )

                    rememberProtectedProcess(protectedProcess)
                    applyProtectedMaxAdj(processRecord)
                    writeOomScoreAdj(newPid, PROTECTED_OOM_ADJ)

                    log.i(
                        "tracked protected process:"
                            + " group=PROCESS_RECORD_KILL"
                            + " method=${method.describeSignature()}"
                            + " pid=$newPid package=$protectedPackage"
                            + " process=${protectedProcess.processName.orEmpty()}",
                    )

                    result
                }

            log.i("hooked ProcessRecord#setPid(int)")
        }.onFailure { error ->
            log.w( "failed to hook ProcessRecord#setPid", error)
        }
    }

    private fun writeOomScoreAdj(
        pid: Int,
        adj: Int,
    ) {
        runCatching {
            File("/proc/$pid/oom_score_adj").writeText(adj.toString())
            log.i( "write oom_score_adj: pid=$pid adj=$adj")
        }.onFailure { error ->
            log.w( "failed to write oom_score_adj: pid=$pid adj=$adj", error)
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

            log.i(
                "skip ProcessList#setOomAdj: no supported signature, candidates=$candidates",
            )

            return
        }

        methods.forEach { method ->
            hookSetOomAdj(method)

            log.i(
                "OomAdjProtectHook installed for ProcessList#setOomAdj: ${method.describeSignature()}",
            )
        }
    }

    private fun hookSetOomAdj(method: Method) {
        if (!rememberHookedMethod(method)) {
            return
        }

        runCatching {
            method.isAccessible = true

            engine.hook(method) { chain ->
                    val args = chain.args

                    val pid = args.getOrNull(0) as? Int
                    val adj = args.getOrNull(2) as? Int

                    if (pid == null || adj == null) {
                        return@hook chain.proceed()
                    }

                    val protectedProcess =
                        protectedProcesses[pid] ?: return@hook chain.proceed()

                    if (!isStillSameProtectedProcess(protectedProcess)) {
                        forgetProtectedProcess(
                            pid = pid,
                            reason = "pid no longer belongs to protected package",
                        )

                        return@hook chain.proceed()
                    }

                    if (adj <= PROTECTED_OOM_ADJ) {
                        return@hook chain.proceed()
                    }

                    val newArgs = args.toTypedArray()
                    newArgs[2] = PROTECTED_OOM_ADJ

                    log.i(
                        "clamp oom adj: group=OOM_ADJ pid=$pid"
                            + " package=${protectedProcess.packageName}"
                            + " method=${method.describeSignature()}"
                            + " $adj->$PROTECTED_OOM_ADJ",
                    )

                    chain.proceed(newArgs)
                }

            log.i(
                "hooked ProcessList#setOomAdj: ${method.describeSignature()}",
            )
        }.onFailure { error ->
            log.w( "failed to hook ProcessList#setOomAdj", error)
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
            log.w( "failed to apply protected max adj", error)
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

        log.i(
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
            val bytes = File("/proc/$pid/cmdline").readBytes()
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
            log.w( "failed to invoke ${receiver.javaClass.name}#$methodName", error)
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
            log.w( "skip OomAdjProtectHook: $className not found", error)
        }.getOrNull()
    }

    private fun observeSettings() {
        updateKeepAlivePackages(initialSettings)
        settingsSubscriptions += settingsProvider.subscribe { state ->
            updateKeepAlivePackages(state.snapshotOrDisabled())
        }
    }

    private fun updateKeepAlivePackages(settings: HookSettingsSnapshot) {
        val packages = KeepAlivePackages.parse(settings.keepAlivePackages)

        keepAlivePackages.set(packages)

        protectedProcesses.forEach { (pid, process) ->
            if (process.packageName !in packages) {
                forgetProtectedProcess(
                    pid = pid,
                    reason = "package removed from keep-alive list",
                )
            }
        }

        log.i(
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
