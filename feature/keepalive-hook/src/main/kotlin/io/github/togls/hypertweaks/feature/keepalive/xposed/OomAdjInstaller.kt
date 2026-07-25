package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookEngine
import io.github.togls.hypertweaks.feature.keepalive.policy.Decision
import io.github.togls.hypertweaks.feature.keepalive.policy.KeepAlivePolicy
import io.github.togls.hypertweaks.logging.api.Logger
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class OomAdjInstaller(
    private val engine: HookEngine,
    private val logger: Logger,
    private val policy: KeepAlivePolicy,
    private val resolver: OomAdjResolver = OomAdjResolver(),
    private val identityResolver: ProcessIdentityResolver = ProcessIdentityResolver(logger),
    private val processAccess: OomAdjProcessAccess = OomAdjProcessAccess(logger),
) {
    private val installationInProgress = AtomicBoolean(false)
    private val installed = AtomicBoolean(false)
    @Volatile
    private var installedTargets: Set<String> = emptySet()
    private val protectedProcesses = ConcurrentHashMap<Int, ProtectedProcess>()

    fun install(classLoader: ClassLoader): HookInstallationReport {
        if (!policy.shouldInstallOomAdjHooks()) {
            logger.info(
                event = "keepalive.oom_adj.install.deferred",
                fields = mapOf("reason" to "empty_package_configuration"),
            )
            return HookInstallationReport.Deferred
        }
        if (installed.get()) {
            return HookInstallationReport.AlreadyInstalled(installedTargets)
        }
        if (!installationInProgress.compareAndSet(false, true)) {
            return HookInstallationReport.Deferred
        }
        return try {
            logger.info(
                event = "target.resolve.started",
                fields = mapOf("subtarget" to "keepalive.oom_adj", "reason" to "install_requested"),
            )
            val resolution = resolver.resolve(classLoader)
            logResolutionFailures(resolution)
            logger.info(
                event = "target.resolve.succeeded",
                fields = mapOf(
                    "subtarget" to "keepalive.oom_adj",
                    "reason" to "resolution_completed",
                    "resolved_count" to
                        (resolution.setPidMethods.size + resolution.setOomAdjMethods.size).toString(),
                ),
            )
            installResolvedTargets(resolution).also { report ->
                installedTargets = report.installedTargets
                installed.set(report.installedTargets.isNotEmpty())
            }
        } finally {
            installationInProgress.set(false)
        }
    }

    fun reconcileConfiguredPackages(configuredPackages: Set<String>) {
        protectedProcesses.forEach { (pid, process) ->
            if (process.packageName !in configuredPackages) {
                forgetProcess(pid, "package_removed_from_configuration")
            }
        }
    }

    internal fun installResolvedTargets(
        resolution: OomAdjResolution,
    ): HookInstallationReport.Completed {
        val installedTargets = mutableSetOf<String>()
        val failedTargets = resolution.failures.keys
            .mapTo(mutableSetOf()) { className -> "resolve:$className" }
        resolution.setPidMethods.forEach { method ->
            installMethod(method, installedTargets, failedTargets, ::interceptSetPid)
        }
        resolution.setOomAdjMethods.forEach { method ->
            installMethod(method, installedTargets, failedTargets, ::interceptSetOomAdj)
        }
        return HookInstallationReport.Completed(installedTargets, failedTargets)
    }

    private fun installMethod(
        method: Method,
        installedTargets: MutableSet<String>,
        failedTargets: MutableSet<String>,
        callback: (Method, HookChain) -> Any?,
    ) {
        val signature = method.describeSignature()
        try {
            method.isAccessible = true
            engine.hook(method) { chain -> callback(method, chain) }
            installedTargets += signature
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            failedTargets += signature
            logger.error(
                event = "keepalive.oom_adj.install.failed",
                throwable = error,
                fields = mapOf("method" to signature),
            )
        }
    }

    private fun interceptSetPid(method: Method, chain: HookChain): Any? {
        logCallbackEvent("hook.callback.entered", "oom_adj.set_pid", "callback_invoked", method)
        val processRecord = chain.thisObject
        val oldPid = processRecord?.let(identityResolver::pid)
        val originalResult = chain.proceed()
        try {
            afterSetPid(method, processRecord, oldPid, chain.args)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.error(
                event = "hook.callback.failed",
                throwable = error,
                fields = mapOf(
                    "subtarget" to "oom_adj.set_pid",
                    "reason" to "post_process_failure",
                    "method" to method.describeSignature(),
                ),
            )
        }
        return originalResult
    }

    private fun afterSetPid(
        method: Method,
        processRecord: Any?,
        oldPid: Int?,
        arguments: List<Any?>,
    ) {
        val newPid = arguments.firstOrNull() as? Int
        if (oldPid != null && oldPid > 0 && oldPid != newPid) {
            forgetProcess(oldPid, "pid_changed")
        }
        if (processRecord == null || newPid == null || newPid <= 0) {
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_pid",
                "invalid_arguments",
                method,
            )
            oldPid?.takeIf { pid -> pid > 0 }?.let { pid ->
                forgetProcess(pid, "invalid_set_pid_arguments")
            }
            return
        }
        registerProcessIfConfigured(method, processRecord, newPid)
    }

    private fun registerProcessIfConfigured(
        method: Method,
        processRecord: Any,
        pid: Int,
    ) {
        val packages = policy.currentConfiguration().packages
        val packageName = identityResolver.fromProcessRecord(processRecord, packages)
        if (packageName == null) {
            forgetProcess(pid, "process_not_configured")
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_pid",
                "package_not_configured",
                method,
            )
            return
        }
        val decision = policy.decideOomAdj(packageName, Int.MAX_VALUE, ProtectedOomAdj)
        val process = ProtectedProcess(pid, packageName, identityResolver.processName(processRecord))
        protectedProcesses[pid] = process
        logPolicyHit(method, process, decision, requestedAdj = null)
        if (decision is Decision.Clamp) {
            processAccess.applyProtectedState(processRecord, ProtectedOomAdj)
            processAccess.writeOomScoreAdj(pid, ProtectedOomAdj)
            logCallbackEvent(
                "hook.callback.transformed",
                "oom_adj.set_pid",
                decision.reason,
                method,
            )
        } else {
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_pid",
                decision.reason,
                method,
            )
        }
    }

    private fun interceptSetOomAdj(method: Method, chain: HookChain): Any? {
        logCallbackEvent("hook.callback.entered", "oom_adj.set_oom_adj", "callback_invoked", method)
        val callbackAction = try {
            evaluateSetOomAdj(method, chain.args)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.error(
                event = "hook.callback.failed",
                throwable = error,
                fields = mapOf(
                    "subtarget" to "oom_adj.set_oom_adj",
                    "reason" to "argument_or_identity_failure",
                    "method" to method.describeSignature(),
                ),
            )
            OomCallbackAction.ProceedOriginal
        }
        return when (callbackAction) {
            OomCallbackAction.ProceedOriginal -> chain.proceed()
            is OomCallbackAction.ProceedWith -> chain.proceed(callbackAction.arguments)
        }
    }

    private fun evaluateSetOomAdj(
        method: Method,
        arguments: List<Any?>,
    ): OomCallbackAction {
        val pid = arguments.getOrNull(0) as? Int
        val requestedAdj = arguments.getOrNull(2) as? Int
        if (pid == null || requestedAdj == null) {
            logInvalidArguments(method, arguments)
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_oom_adj",
                "invalid_arguments",
                method,
            )
            return OomCallbackAction.ProceedOriginal
        }
        val process = protectedProcesses[pid]
        if (process == null) {
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_oom_adj",
                "process_not_tracked",
                method,
            )
            return OomCallbackAction.ProceedOriginal
        }
        if (!processAccess.isSameProcess(process)) {
            forgetProcess(pid, "pid_identity_changed")
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_oom_adj",
                "pid_identity_changed",
                method,
            )
            return OomCallbackAction.ProceedOriginal
        }
        return evaluateOomDecision(method, arguments, process, requestedAdj)
    }

    private fun evaluateOomDecision(
        method: Method,
        arguments: List<Any?>,
        process: ProtectedProcess,
        requestedAdj: Int,
    ): OomCallbackAction {
        val decision = try {
            policy.decideOomAdj(process.packageName, requestedAdj, ProtectedOomAdj)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.error(
                event = "hook.callback.failed",
                throwable = error,
                fields = mapOf(
                    "subtarget" to "oom_adj.set_oom_adj",
                    "reason" to "policy_failure",
                    "method" to method.describeSignature(),
                ),
            )
            return OomCallbackAction.ProceedOriginal
        }
        logPolicyHit(method, process, decision, requestedAdj)
        if (decision !is Decision.Clamp) {
            logCallbackEvent(
                "hook.callback.bypassed",
                "oom_adj.set_oom_adj",
                decision.reason,
                method,
            )
            return OomCallbackAction.ProceedOriginal
        }
        val replacementArguments = arguments.toTypedArray()
        replacementArguments[2] = ProtectedOomAdj
        logCallbackEvent(
            "hook.callback.transformed",
            "oom_adj.set_oom_adj",
            decision.reason,
            method,
        )
        return OomCallbackAction.ProceedWith(replacementArguments)
    }

    private fun logPolicyHit(
        method: Method,
        process: ProtectedProcess,
        decision: Decision,
        requestedAdj: Int?,
    ) {
        if (decision.packageName == null) return
        logger.info(
            event = "keepalive.oom_adj.policy_hit",
            fields = mapOf(
                "action" to decision.actionName(),
                "reason" to decision.reason,
                "package" to process.packageName,
                "pid" to process.pid.toString(),
                "requested_adj" to requestedAdj?.toString().orEmpty(),
                "method" to method.describeSignature(),
            ),
        )
    }

    private fun logInvalidArguments(method: Method, arguments: List<Any?>) {
        logger.warn(
            event = "keepalive.oom_adj.arguments.invalid",
            fields = mapOf(
                "method" to method.describeSignature(),
                "argument_count" to arguments.size.toString(),
            ),
        )
    }

    private fun logCallbackEvent(
        event: String,
        subtarget: String,
        reason: String,
        method: Method,
    ) {
        logger.debug(
            event = event,
            fields = mapOf(
                "subtarget" to subtarget,
                "reason" to reason,
                "method" to method.describeSignature(),
            ),
        )
    }

    private fun forgetProcess(pid: Int, reason: String) {
        val removed = protectedProcesses.remove(pid) ?: return
        logger.info(
            event = "keepalive.oom_adj.process.forgotten",
            fields = mapOf(
                "pid" to pid.toString(),
                "package" to removed.packageName,
                "reason" to reason,
            ),
        )
    }

    private fun logResolutionFailures(resolution: OomAdjResolution) {
        resolution.failures.forEach { (className, error) ->
            logger.error(
                event = "target.resolve.failed",
                throwable = error,
                fields = mapOf(
                    "subtarget" to "keepalive.oom_adj",
                    "reason" to "class_resolution_failure",
                    "class" to className,
                ),
            )
        }
    }

    companion object {
        internal const val ProtectedOomAdj = 200
    }
}

internal data class ProtectedProcess(
    val pid: Int,
    val packageName: String,
    val processName: String?,
)

private sealed interface OomCallbackAction {
    data object ProceedOriginal : OomCallbackAction

    data class ProceedWith(
        val arguments: Array<out Any?>,
    ) : OomCallbackAction
}

internal open class OomAdjProcessAccess(
    private val logger: Logger,
) {
    open fun applyProtectedState(processRecord: Any, protectedAdj: Int) {
        val state = readFieldValue(processRecord, "mState")
            ?: readFieldValue(processRecord, "state")
            ?: return
        StateMutationMethods.forEach { methodName ->
            invokeIntMethod(state, methodName, protectedAdj)
        }
    }

    open fun writeOomScoreAdj(pid: Int, protectedAdj: Int) {
        try {
            File("/proc/$pid/oom_score_adj").writeText(protectedAdj.toString())
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.warn(
                event = "keepalive.oom_adj.proc_write.failed",
                throwable = error,
                fields = mapOf("pid" to pid.toString(), "adj" to protectedAdj.toString()),
            )
        }
    }

    open fun isSameProcess(process: ProtectedProcess): Boolean {
        val commandLine = readProcessCommandLine(process.pid) ?: return false
        return commandLine == process.packageName ||
            commandLine == process.processName ||
            commandLine.startsWith("${process.packageName}:") ||
            process.processName?.let { name -> commandLine.startsWith("$name:") } == true
    }

    private fun readProcessCommandLine(pid: Int): String? {
        return try {
            val bytes = File("/proc/$pid/cmdline").readBytes()
            bytes.takeWhile { byte -> byte.toInt() != 0 }
                .toByteArray()
                .toString(Charsets.UTF_8)
                .trim()
                .takeIf(String::isNotEmpty)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.warn(
                event = "keepalive.oom_adj.proc_read.failed",
                throwable = error,
                fields = mapOf("pid" to pid.toString()),
            )
            null
        }
    }

    private fun readFieldValue(receiver: Any, fieldName: String): Any? {
        val field = generateSequence(receiver.javaClass) { type -> type.superclass }
            .mapNotNull { type ->
                try {
                    type.getDeclaredField(fieldName).apply { isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    null
                }
            }
            .firstOrNull()
            ?: return null
        return try {
            field.get(receiver)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.warn(
                event = "keepalive.oom_adj.state_read.failed",
                throwable = error,
                fields = mapOf("field" to fieldName),
            )
            null
        }
    }

    private fun invokeIntMethod(receiver: Any, methodName: String, value: Int) {
        val method = findIntMethod(receiver.javaClass, methodName) ?: return
        try {
            method.invoke(receiver, value)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.warn(
                event = "keepalive.oom_adj.state_mutation.failed",
                throwable = error,
                fields = mapOf("method" to methodName),
            )
        }
    }

    private fun findIntMethod(type: Class<*>, methodName: String): Method? {
        val intType = Int::class.javaPrimitiveType
        return generateSequence(type) { currentType -> currentType.superclass }
            .flatMap { currentType -> currentType.declaredMethods.asSequence() }
            .firstOrNull { method ->
                method.name == methodName &&
                    method.parameterTypes.contentEquals(arrayOf(intType))
            }
            ?.apply { isAccessible = true }
    }

    private companion object {
        private val StateMutationMethods = listOf(
            "setMaxAdj",
            "setCurAdj",
            "setCurRawAdj",
            "setSetAdj",
            "setSetRawAdj",
        )
    }
}
