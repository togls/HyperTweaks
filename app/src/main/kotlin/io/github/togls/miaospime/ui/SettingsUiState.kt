package io.github.togls.miaospime.ui

import io.github.togls.miaospime.data.NavBarLayoutConfig

data class SettingsUiState(
    val serviceConnected: Boolean = false,
    val config: NavBarLayoutConfig = NavBarLayoutConfig(),
    val message: String = "",
)