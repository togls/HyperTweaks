package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.core.xposed.snapshotOrDisabled
import io.github.togls.hypertweaks.feature.keepalive.policy.KeepAlivePolicy
import java.util.concurrent.atomic.AtomicBoolean

class KeepAliveHook(
    private val context: HookContext,
) {
    private val policy = KeepAlivePolicy(context.settings)
    private val installer = ProcessKillInstaller(
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
                event = "keepalive.process_kill.runtime.duplicate_start_ignored",
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
                event = "keepalive.process_kill.settings_subscription.failed",
                throwable = error,
            )
        }
    }

    private fun applySettings(settings: HookSettingsSnapshot) {
        try {
            val configuration = policy.update(settings)
            context.log.info(
                event = "keepalive.process_kill.configuration.updated",
                fields = mapOf(
                    "mode" to configuration.mode.value,
                    "package_count" to configuration.packages.size.toString(),
                ),
            )
            installer.install(systemServerClassLoader)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            context.log.error(
                event = "keepalive.process_kill.configuration.failed_open",
                throwable = error,
            )
            policy.update(HookSettingsSnapshot.Disabled)
        }
    }
}
