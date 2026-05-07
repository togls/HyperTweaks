package io.github.togls.hypertweaks.ui.settings

import io.github.togls.hypertweaks.core.config.HyperTweaksConfig
import io.github.togls.hypertweaks.feature.ime.ui.ImeSettingsUiState
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import io.github.togls.hypertweaks.feature.keepalive.ui.KeepAliveSettingsUiState

data class SettingsUiState(
    val service: SettingsServiceUiState = SettingsServiceUiState(),
    val ime: ImeSettingsUiState = ImeSettingsUiState(),
    val keepAlive: KeepAliveSettingsUiState = KeepAliveSettingsUiState(),
)

data class SettingsServiceUiState(
    val connected: Boolean = false,
    val message: String = "",
)

fun HyperTweaksConfig.toSettingsUiState(
    serviceConnected: Boolean,
    message: String,
): SettingsUiState {
    return SettingsUiState(
        service = SettingsServiceUiState(
            connected = serviceConnected,
            message = message,
        ),
        ime = ImeSettingsUiState(
            enabled = features.imeEnabled,
            navBarLayout = ime.navBarLayout,
        ),
        keepAlive = KeepAliveSettingsUiState(
            enabled = features.keepAliveEnabled,
            mode = keepAlive.mode,
            packagesText = KeepAlivePackages.format(keepAlive.packages),
            invalidPackages = emptyList(),
        ),
    )
}
