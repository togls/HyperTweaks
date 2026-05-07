package io.github.togls.hypertweaks.feature.keepalive.ui

import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode

data class KeepAliveSettingsUiState(
    val enabled: Boolean = false,
    val mode: KeepAliveMode = KeepAliveMode.Default,
    val packagesText: String = "",
    val invalidPackages: List<String> = emptyList(),
)