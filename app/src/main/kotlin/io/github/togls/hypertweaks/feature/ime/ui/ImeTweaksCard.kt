package io.github.togls.hypertweaks.feature.ime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.ui.components.AppPreferenceGroup
import io.github.togls.hypertweaks.ui.components.AppSwitchPreference

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
    val controlsEnabled = true  // serviceConnected && uiState.enabled

    AppPreferenceGroup(
        modifier = modifier,
    ) {
        AppSwitchPreference(
            title = stringResource(R.string.feature_ime_title),
            summary = stringResource(R.string.feature_ime_description),
            checked = uiState.enabled,
            enabled = controlsEnabled,
            onCheckedChange = onImeEnabledChange,
        )

        Column(
            modifier = Modifier.alpha(if (uiState.enabled) 1f else 0.5f),
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
                HandlePreviewCard(
                    handleLayout = uiState.navBarLayout.toHandleLayout(),
                )
            }
        }
    }
}
