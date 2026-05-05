package io.github.togls.hypertweaks.ui

import io.github.togls.hypertweaks.data.NavBarLayoutConfig

data class SettingsUiState(
    val serviceConnected: Boolean = false,
    val config: NavBarLayoutConfig = NavBarLayoutConfig(),
    val message: String = "",
)