package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.core.xposed.snapshotOrDisabled
import io.github.togls.hypertweaks.feature.keepalive.policy.KeepAlivePolicy
import java.util.concurrent.atomic.AtomicBoolean

class OomAdjProtectHook(
    private val context: HookContext,
) {
    private val policy = KeepAlivePolicy(context.settings)
    private val installer = OomAdjInstaller(
        engine = context.engine,
        logger = context.log,
        policy = policy,
    )
    private val runtimeStarted = AtomicBoolean(false)
    private var settingsSubscription: HookSettingsSubscription? = null
    private lateinit var systemServerClassLoader: ClassLoader

    fun installSystemServer(classLoader: ClassLoader) {
        if (!runtimeStarted.compareAndSet(false, true)) {
            context.log.info(
                event = "keepalive.oom_adj.runtime.duplicate_start_ignored",
            )
            return
        }
        systemServerClassLoader = classLoader
        applySettings(context.settings)
        observeSettings()
    }

    private fun observeSettings() {
        try {
            settingsSubscription = context.settingsProvider.subscribe { state ->
                applySettings(state.snapshotOrDisabled())
            }
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            context.log.error(
                event = "keepalive.oom_adj.settings_subscription.failed",
                throwable = error,
            )
        }
    }

    private fun applySettings(settings: HookSettingsSnapshot) {
        try {
            val configuration = policy.update(settings)
            installer.reconcileConfiguredPackages(configuration.packages)
            context.log.info(
                event = "keepalive.oom_adj.configuration.updated",
                fields = mapOf(
                    "mode" to configuration.mode.value,
                    "package_count" to configuration.packages.size.toString(),
                ),
            )
            installer.install(systemServerClassLoader)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            context.log.error(
                event = "keepalive.oom_adj.configuration.failed_open",
                throwable = error,
            )
            val disabledConfiguration = policy.update(HookSettingsSnapshot.Disabled)
            installer.reconcileConfiguredPackages(disabledConfiguration.packages)
        }
    }
}
