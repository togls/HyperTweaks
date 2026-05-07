package io.github.togls.hypertweaks.feature.ime.ui

import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig

data class ImeSettingsUiState(
    val enabled: Boolean = false,
    val navBarLayout: NavBarLayoutConfig = NavBarLayoutConfig(),
)