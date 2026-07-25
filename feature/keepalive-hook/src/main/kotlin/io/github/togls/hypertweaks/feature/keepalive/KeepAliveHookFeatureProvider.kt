package io.github.togls.hypertweaks.feature.keepalive

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookFeatureProvider
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookTarget
import io.github.togls.hypertweaks.feature.keepalive.xposed.KeepAliveHook
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
        KeepAliveHook(context.child("KeepAliveHook"))
            .installSystemServer(context.environment.classLoader)
        return HookInstallResult.Installed(installedTargets = setOf("process_kill_runtime"))
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
        OomAdjProtectHook(context.child("OomAdjProtectHook"))
            .installSystemServer(context.environment.classLoader)
        return HookInstallResult.Installed(installedTargets = setOf("oom_adj_runtime"))
    }
}
