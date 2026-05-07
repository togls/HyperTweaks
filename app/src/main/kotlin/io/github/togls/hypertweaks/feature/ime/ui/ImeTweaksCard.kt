package io.github.togls.hypertweaks.feature.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.ui.component.FeatureSwitchRow
import io.github.togls.hypertweaks.ui.component.SettingsSectionCard

@Composable
fun ImeTweaksCard(
    serviceConnected: Boolean,
    uiState: ImeSettingsUiState,
    showDebugInfo: Boolean,
    onImeEnabledChange: (Boolean) -> Unit,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsEnabled = serviceConnected && uiState.enabled

    SettingsSectionCard(
        modifier = modifier,
    ) {
        FeatureSwitchRow(
            title = stringResource(R.string.feature_ime_title),
            description = stringResource(R.string.feature_ime_description),
            checked = uiState.enabled,
            enabled = serviceConnected,
            onCheckedChange = onImeEnabledChange,
        )

        HorizontalDivider()

        Column(
            modifier = Modifier.alpha(if (uiState.enabled) 1f else 0.5f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NavBarButtonSelector(
                title = stringResource(R.string.start_button_title),
                description = stringResource(R.string.start_button_description),
                selected = uiState.navBarLayout.start,
                enabled = controlsEnabled,
                onSelectedChange = onStartButtonChange,
            )

            NavBarButtonSelector(
                title = stringResource(R.string.end_button_title),
                description = stringResource(R.string.end_button_description),
                selected = uiState.navBarLayout.end,
                enabled = controlsEnabled,
                onSelectedChange = onEndButtonChange,
            )

            if (showDebugInfo) {
                HorizontalDivider()

                HandlePreviewCard(
                    handleLayout = uiState.navBarLayout.toHandleLayout(),
                )
            }
        }
    }
}