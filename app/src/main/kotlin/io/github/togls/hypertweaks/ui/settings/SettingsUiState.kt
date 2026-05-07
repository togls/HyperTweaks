package io.github.togls.hypertweaks.ui.settings

import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode

data class SettingsUiState(
    val serviceConnected: Boolean = false,
    val imeEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val config: NavBarLayoutConfig = NavBarLayoutConfig(),
    val keepAliveMode: KeepAliveMode = KeepAliveMode.Default,
    val keepAlivePackagesText: String = "",
    val invalidKeepAlivePackages: List<String> = emptyList(),
    val message: String = "",
)
