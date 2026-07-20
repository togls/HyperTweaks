package io.github.togls.hypertweaks.feature.keepalive.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.ui.components.AppPreferenceGroup
import io.github.togls.hypertweaks.ui.components.AppSwitchPreference

@Composable
fun KeepAliveTweaksCard(
    serviceConnected: Boolean,
    uiState: KeepAliveSettingsUiState,
    onKeepAliveEnabledChange: (Boolean) -> Unit,
    onKeepAliveModeChange: (KeepAliveMode) -> Unit,
    onPackagesTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsEnabled = true  // serviceConnected && uiState.enabled

    AppPreferenceGroup(
        modifier = modifier,
    ) {
        AppSwitchPreference(
            title = stringResource(R.string.feature_keep_alive_title),
            summary = stringResource(R.string.feature_keep_alive_description),
            checked = uiState.enabled,
            enabled = controlsEnabled,
            onCheckedChange = onKeepAliveEnabledChange,
        )

        KeepAliveModeSelector(
            selectedMode = uiState.mode,
            enabled = controlsEnabled,
            onModeChange = onKeepAliveModeChange,
        )

        KeepAlivePackagesEditor(
            packagesText = uiState.packagesText,
            invalidPackages = uiState.invalidPackages,
            enabled = controlsEnabled,
            onPackagesTextChange = onPackagesTextChange,
            onSaveClick = onSaveClick,
        )
    }
}
