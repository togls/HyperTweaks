package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.xposed.HookEngine
import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookHandle
import io.github.togls.hypertweaks.core.xposed.HookInstallGuard
import io.github.togls.hypertweaks.core.xposed.HookInstallKey
import io.github.togls.hypertweaks.core.xposed.HookInstallState
import io.github.togls.hypertweaks.core.xposed.HookInterceptor
import io.github.togls.hypertweaks.core.xposed.HookSettingsProvider
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsState
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogSource
import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.logging.api.LoggerFactory
import java.lang.reflect.Executable

internal fun imeTestContext(
    sdkInt: Int,
    packageName: String = "system_server",
    isSystemServer: Boolean = packageName == "system_server",
    events: MutableList<LogEvent> = mutableListOf(),
): HookFeatureContext {
    val settings = HookSettingsSnapshot()
    return HookFeatureContext(
        environment = HookEnvironment(
            packageName = packageName,
            processName = packageName,
            classLoader = ClassLoader.getSystemClassLoader(),
            sdkInt = sdkInt,
            sessionId = "ime-test",
            isSystemServer = isSystemServer,
        ),
        engine = UnusedHookEngine,
        settings = settings,
        logger = eventLogger(events),
        installGuard = UnusedInstallGuard,
        settingsProvider = StaticSettingsProvider(settings),
    )
}

internal fun eventLogger(events: MutableList<LogEvent>): Logger {
    return LoggerFactory.create(
        source = LogSource.HOOK,
        modeProvider = { LogMode.DEBUG },
        sink = LogSink(events::add),
    )
}

private object UnusedHookEngine : HookEngine {
    override fun hook(executable: Executable, interceptor: HookInterceptor): HookHandle {
        error("Hook engine should not be used by strategy unit tests")
    }

    override fun deoptimize(executable: Executable): Boolean = false
}

private object UnusedInstallGuard : HookInstallGuard {
    override fun tryStart(key: HookInstallKey): Boolean = true
    override fun markInstalled(key: HookInstallKey) = Unit
    override fun markFailed(key: HookInstallKey) = Unit
    override fun state(key: HookInstallKey): HookInstallState = HookInstallState.NEW
}

private class StaticSettingsProvider(
    snapshot: HookSettingsSnapshot,
) : HookSettingsProvider {
    override val currentState: HookSettingsState = HookSettingsState.Ready(snapshot)

    override fun subscribe(listener: (HookSettingsState) -> Unit): HookSettingsSubscription {
        return HookSettingsSubscription {}
    }
}
