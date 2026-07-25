package io.github.togls.hypertweaks.feature.keepalive

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookFeatureProvider
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookTarget
import io.github.togls.hypertweaks.feature.keepalive.xposed.KeepAliveHook
import io.github.togls.hypertweaks.feature.keepalive.xposed.HookInstallationReport
import io.github.togls.hypertweaks.feature.keepalive.xposed.OomAdjProtectHook

class KeepAliveHookFeatureProvider(
    private val systemServerFeatureEnabled: () -> Boolean = { true },
) : HookFeatureProvider {
    override fun features(): List<HookFeature> {
        return listOf(
            KeepAliveProcessKillFeature(systemServerFeatureEnabled),
            OomAdjProtectFeature(systemServerFeatureEnabled),
        )
    }
}

private class KeepAliveProcessKillFeature(
    private val systemServerFeatureEnabled: () -> Boolean,
) : HookFeature {
    override val id: String = "keepalive.process-kill"
    override val preferenceKey: String = RemotePreferenceKeys.KeepAliveEnabled
    override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

    override fun supports(environment: HookEnvironment): Boolean {
        return systemServerFeatureEnabled() && super.supports(environment)
    }

    override fun install(context: HookFeatureContext): HookInstallResult {
        if (!systemServerFeatureEnabled()) {
            return HookInstallResult.Unsupported("keepalive system_server safety switch is disabled")
        }
        val report = KeepAliveHook(context.child("KeepAliveHook"))
            .installSystemServer(context.environment.classLoader)
        return report.toHookInstallResult(
            capability = "process_kill",
            unsupportedReason = "No supported process-kill Hook target was resolved",
        )
    }
}

private class OomAdjProtectFeature(
    private val systemServerFeatureEnabled: () -> Boolean,
) : HookFeature {
    override val id: String = "keepalive.oom-adj"
    override val preferenceKey: String = RemotePreferenceKeys.KeepAliveEnabled
    override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

    override fun supports(environment: HookEnvironment): Boolean {
        return systemServerFeatureEnabled() && super.supports(environment)
    }

    override fun install(context: HookFeatureContext): HookInstallResult {
        if (!systemServerFeatureEnabled()) {
            return HookInstallResult.Unsupported("keepalive system_server safety switch is disabled")
        }
        val report = OomAdjProtectHook(context.child("OomAdjProtectHook"))
            .installSystemServer(context.environment.classLoader)
        return report.toHookInstallResult(
            capability = "oom_adj",
            unsupportedReason = "No supported OOM-adj Hook target was resolved",
        )
    }
}

private fun HookInstallationReport.toHookInstallResult(
    capability: String,
    unsupportedReason: String,
): HookInstallResult {
    return when (this) {
        HookInstallationReport.Deferred -> HookInstallResult.Deferred(
            "$capability is waiting for a compatible non-empty configuration",
        )
        is HookInstallationReport.AlreadyInstalled -> HookInstallResult.Installed(
            installedTargets = installedTargets.mapTo(mutableSetOf()) { target ->
                "$capability:$target"
            },
        )
        is HookInstallationReport.Failed -> HookInstallResult.Failed(error)
        is HookInstallationReport.Completed -> when {
            installedTargets.isNotEmpty() -> HookInstallResult.Installed(
                installedTargets = installedTargets.mapTo(mutableSetOf()) { target ->
                    "$capability:$target"
                },
                failedTargets = failedTargets,
            )
            failedTargets.isNotEmpty() -> HookInstallResult.Failed(
                IllegalStateException(
                    "$capability Hook installation failed: ${failedTargets.sorted().joinToString()}",
                ),
            )
            else -> HookInstallResult.Unsupported(unsupportedReason)
        }
    }
}
