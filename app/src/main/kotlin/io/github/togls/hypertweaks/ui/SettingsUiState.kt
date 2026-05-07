package io.github.togls.hypertweaks.ui

import io.github.togls.hypertweaks.data.NavBarLayoutConfig

data class SettingsUiState(
    val serviceConnected: Boolean = false,
    val imeEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val config: NavBarLayoutConfig = NavBarLayoutConfig(),
    val keepAlivePackagesText: String = "",
    val invalidKeepAlivePackages: List<String> = emptyList(),
    val message: String = "",
)
