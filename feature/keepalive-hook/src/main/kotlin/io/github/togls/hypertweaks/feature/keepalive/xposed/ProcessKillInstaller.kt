package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookEngine
import io.github.togls.hypertweaks.feature.keepalive.policy.Decision
import io.github.togls.hypertweaks.feature.keepalive.policy.KeepAlivePolicy
import io.github.togls.hypertweaks.logging.api.Logger
import java.util.concurrent.atomic.AtomicBoolean

internal class ProcessKillInstaller(
    private val engine: HookEngine,
    private val logger: Logger,
    private val policy: KeepAlivePolicy,
    private val resolver: ProcessKillResolver = ProcessKillResolver(),
    private val identityResolver: ProcessIdentityResolver = ProcessIdentityResolver(logger),
) {
    private val installationStarted = AtomicBoolean(false)

    fun install(classLoader: ClassLoader): HookInstallationReport {
        if (!policy.shouldInstallProcessKillHooks()) {
            logger.info(
                event = "keepalive.process_kill.install.deferred",
                fields = mapOf("reason" to "mode_or_package_configuration"),
            )
            return HookInstallationReport.Deferred
        }
        if (!installationStarted.compareAndSet(false, true)) {
            return HookInstallationReport.AlreadyInstalled
        }
        logger.info(
            event = "target.resolve.started",
            fields = mapOf("subtarget" to "keepalive.process_kill", "reason" to "install_requested"),
        )
        val resolution = resolver.resolve(classLoader)
        logResolutionFailures(resolution)
        logger.info(
            event = "target.resolve.succeeded",
            fields = mapOf(
                "subtarget" to "keepalive.process_kill",
                "reason" to "resolution_completed",
                "resolved_count" to resolution.targets.size.toString(),
            ),
        )
        return installResolvedTargets(resolution.targets)
    }

    internal fun installResolvedTargets(
        targets: List<ProcessKillTarget>,
    ): HookInstallationReport.Completed {
        val installedTargets = mutableSetOf<String>()
        val failedTargets = mutableSetOf<String>()
        targets.forEach { target ->
            val signature = target.method.describeSignature()
            if (installTarget(target)) {
                installedTargets += signature
            } else {
                failedTargets += signature
            }
        }
        logger.info(
            event = "keepalive.process_kill.install.completed",
            fields = mapOf(
                "installed_count" to installedTargets.size.toString(),
                "failed_count" to failedTargets.size.toString(),
            ),
        )
        return HookInstallationReport.Completed(installedTargets, failedTargets)
    }

    private fun installTarget(target: ProcessKillTarget): Boolean {
        return try {
            target.method.isAccessible = true
            engine.hook(target.method) { chain -> intercept(target, chain) }
            true
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.error(
                event = "keepalive.process_kill.install.failed",
                throwable = error,
                fields = mapOf("method" to target.method.describeSignature()),
            )
            false
        }
    }

    private fun intercept(target: ProcessKillTarget, chain: HookChain): Any? {
        logCallbackEvent("hook.callback.entered", target, "callback_invoked")
        val decision = resolveDecision(target, chain)
        if (decision == null) {
            logCallbackEvent("hook.callback.bypassed", target, "package_not_configured")
            return chain.proceed()
        }
        logPolicyHit(target, decision)
        return when (decision) {
            is Decision.Block -> {
                logCallbackEvent("hook.callback.transformed", target, decision.reason)
                defaultReturnValue(target.method.returnType)
            }
            is Decision.Allow,
            is Decision.Audit,
            is Decision.Clamp,
            -> {
                logCallbackEvent("hook.callback.bypassed", target, decision.reason)
                chain.proceed()
            }
        }
    }

    private fun resolveDecision(
        target: ProcessKillTarget,
        chain: HookChain,
    ): Decision? {
        return try {
            val packages = policy.currentConfiguration().packages
            val packageName = if (target.packageFromReceiver) {
                identityResolver.fromProcessRecord(chain.thisObject, packages)
            } else {
                identityResolver.fromArguments(chain.args, packages)
            } ?: return null
            policy.decideProcessKill(packageName, target.group)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.error(
                event = "hook.callback.failed",
                throwable = error,
                fields = mapOf(
                    "subtarget" to target.group.persistedName,
                    "reason" to "policy_or_identity_failure",
                    "method" to target.method.describeSignature(),
                ),
            )
            null
        }
    }

    private fun logPolicyHit(
        target: ProcessKillTarget,
        decision: Decision,
    ) {
        logger.info(
            event = "keepalive.process_kill.policy_hit",
            fields = mapOf(
                "action" to decision.actionName(),
                "reason" to decision.reason,
                "package" to decision.packageName.orEmpty(),
                "group" to target.group.persistedName,
                "method" to target.method.describeSignature(),
            ),
        )
    }

    private fun logCallbackEvent(
        event: String,
        target: ProcessKillTarget,
        reason: String,
    ) {
        logger.debug(
            event = event,
            fields = mapOf(
                "subtarget" to target.group.persistedName,
                "reason" to reason,
                "method" to target.method.describeSignature(),
            ),
        )
    }

    private fun logResolutionFailures(resolution: ProcessKillResolution) {
        resolution.failures.forEach { (className, error) ->
            logger.error(
                event = "target.resolve.failed",
                throwable = error,
                fields = mapOf(
                    "subtarget" to "keepalive.process_kill",
                    "reason" to "class_resolution_failure",
                    "class" to className,
                ),
            )
        }
        if (resolution.unavailableClasses.isNotEmpty()) {
            logger.info(
                event = "keepalive.process_kill.resolve.optional_unavailable",
                fields = mapOf("classes" to resolution.unavailableClasses.sorted().joinToString()),
            )
        }
    }
}

internal sealed interface HookInstallationReport {
    data object Deferred : HookInstallationReport
    data object AlreadyInstalled : HookInstallationReport

    data class Completed(
        val installedTargets: Set<String>,
        val failedTargets: Set<String>,
    ) : HookInstallationReport
}

internal fun Decision.actionName(): String {
    return when (this) {
        is Decision.Allow -> "allow"
        is Decision.Audit -> "audit"
        is Decision.Block -> "block"
        is Decision.Clamp -> "clamp"
    }
}
