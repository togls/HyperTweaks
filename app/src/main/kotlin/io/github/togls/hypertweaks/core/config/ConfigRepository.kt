package io.github.togls.hypertweaks.core.config

import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.logging.api.LogMode

interface ConfigRepository {
    val serviceConnected: Boolean

    fun loadConfig(): Result<HyperTweaksConfig>

    fun loadLogMode(): Result<LogMode>

    fun saveLogMode(mode: LogMode): Result<LogMode>

    fun saveImeConfig(config: ImeConfig): Result<ImeConfig>

    fun saveFeatureToggles(toggles: FeatureToggles): Result<FeatureToggles>

    fun saveKeepAliveConfig(config: KeepAliveConfig): Result<KeepAliveConfig>

    fun saveNavBarLayout(config: NavBarLayoutConfig): Result<NavBarLayoutConfig>

    fun saveKeepAliveMode(mode: KeepAliveMode): Result<KeepAliveMode>

    fun saveKeepAlivePackages(packages: Set<String>): Result<Set<String>>
}
