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
    connected: Boolean,
    enabled: Boolean,
    mode: KeepAliveMode,
    onKeepAliveEnabledChange: (Boolean) -> Unit,
    onKeepAliveModeChange: (KeepAliveMode) -> Unit,
    packagesText: String,
    invalidPackages: List<String>,
    onPackagesTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(
        modifier = modifier,
    ) {
        FeatureSwitchRow(
            title = stringResource(R.string.feature_keep_alive_title),
            description = stringResource(R.string.feature_keep_alive_description),
            checked = enabled,
            enabled = connected,
            onCheckedChange = onKeepAliveEnabledChange,
        )

        HorizontalDivider()

        KeepAliveModeSelector(
            selectedMode = mode,
            enabled = connected && enabled,
            onModeChange = onKeepAliveModeChange,
        )

        KeepAlivePackagesContent(
            packagesText = packagesText,
            invalidPackages = invalidPackages,
            enabled = connected,
            onPackagesTextChange = onPackagesTextChange,
            onSaveClick = onSaveClick,
        )
    }
}