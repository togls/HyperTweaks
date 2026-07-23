package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger

data class HookContext(
    val environment: HookEnvironment,
    val engine: HookEngine,
    val settings: HookSettingsSnapshot,
    val logger: Logger,
    val installGuard: HookInstallGuard,
    val settingsProvider: HookSettingsProvider,
) {
    val log: Logger
        get() = logger

    fun child(component: String): HookContext {
        return copy(logger = logger.child(component))
    }
}

typealias HookFeatureContext = HookContext
