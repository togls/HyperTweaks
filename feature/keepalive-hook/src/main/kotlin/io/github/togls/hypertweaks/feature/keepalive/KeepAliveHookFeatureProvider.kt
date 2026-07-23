package io.github.togls.hypertweaks.feature.keepalive

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookFeatureProvider
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookTarget
import io.github.togls.hypertweaks.feature.keepalive.xposed.KeepAliveHook
import io.github.togls.hypertweaks.feature.keepalive.xposed.OomAdjProtectHook

class KeepAliveHookFeatureProvider : HookFeatureProvider {
    override fun features(): List<HookFeature> {
        return listOf(KeepAliveProcessKillFeature, OomAdjProtectFeature)
    }
}

private object KeepAliveProcessKillFeature : HookFeature {
    override val id: String = "keepalive.process-kill"
    override val preferenceKey: String = RemotePreferenceKeys.KeepAliveEnabled
    override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

    override fun install(context: HookFeatureContext): HookInstallResult {
        KeepAliveHook(context.child("KeepAliveHook"))
            .installSystemServer(context.environment.classLoader)
        return HookInstallResult.Installed(installedTargets = setOf("process_kill"))
    }
}

private object OomAdjProtectFeature : HookFeature {
    override val id: String = "keepalive.oom-adj"
    override val preferenceKey: String = RemotePreferenceKeys.KeepAliveEnabled
    override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

    override fun install(context: HookFeatureContext): HookInstallResult {
        OomAdjProtectHook(context.child("OomAdjProtectHook"))
            .installSystemServer(context.environment.classLoader)
        return HookInstallResult.Installed(installedTargets = setOf("oom_adj"))
    }
}
