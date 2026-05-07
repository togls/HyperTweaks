package io.github.togls.hypertweaks.feature.keepalive.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.ui.component.FeatureSwitchRow
import io.github.togls.hypertweaks.ui.component.SettingsSectionCard

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

    SettingsSectionCard(
        modifier = modifier,
    ) {
        FeatureSwitchRow(
            title = stringResource(R.string.feature_keep_alive_title),
            description = stringResource(R.string.feature_keep_alive_description),
            checked = uiState.enabled,
            enabled = controlsEnabled,
            onCheckedChange = onKeepAliveEnabledChange,
        )

        HorizontalDivider()

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