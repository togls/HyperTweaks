package io.github.togls.hypertweaks.core.config

import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig

interface ConfigRepository {

    val serviceConnected: Boolean

    fun loadConfig(): Result<NavBarLayoutConfig>

    fun saveConfig(config: NavBarLayoutConfig): Result<NavBarLayoutConfig>

    fun loadFeatureToggles(): Result<FeatureToggles>

    fun saveFeatureToggles(toggles: FeatureToggles): Result<FeatureToggles>

    fun loadKeepAlivePackages(): Result<Set<String>>

    fun saveKeepAlivePackages(packages: Set<String>): Result<Set<String>>
}