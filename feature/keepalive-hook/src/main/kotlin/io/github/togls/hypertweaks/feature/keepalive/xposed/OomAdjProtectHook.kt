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
    @Volatile
    private var lastInstallReport: HookInstallationReport = HookInstallationReport.Deferred
    private var settingsSubscription: HookSettingsSubscription? = null
    private lateinit var systemServerClassLoader: ClassLoader

    internal fun installSystemServer(classLoader: ClassLoader): HookInstallationReport {
        if (!runtimeStarted.compareAndSet(false, true)) {
            context.log.info(
                event = "keepalive.oom_adj.runtime.duplicate_start_ignored",
            )
            return lastInstallReport
        }
        systemServerClassLoader = classLoader
        val report = applySettings(context.settings)
        lastInstallReport = report
        if (report.hasInstalledTargets()) {
            observeSettings()
        } else {
            runtimeStarted.set(false)
        }
        return report
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

    private fun applySettings(settings: HookSettingsSnapshot): HookInstallationReport {
        return try {
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
            HookInstallationReport.Failed(error)
        }
    }
}
