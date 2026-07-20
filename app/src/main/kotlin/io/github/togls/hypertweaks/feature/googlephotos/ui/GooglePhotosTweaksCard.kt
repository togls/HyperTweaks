package io.github.togls.hypertweaks.feature.googlephotos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.ui.component.FeatureSwitchRow
import io.github.togls.hypertweaks.ui.component.SettingsSectionCard

@Composable
fun GooglePhotosTweaksCard(
    uiState: GooglePhotosSettingsUiState,
    onLocationEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(
        modifier = modifier,
    ) {
        FeatureSwitchRow(
            title = stringResource(R.string.feature_google_photos_location_title),
            description = stringResource(R.string.feature_google_photos_location_description),
            checked = uiState.enabled,
            enabled = true,
            onCheckedChange = onLocationEnabledChange,
        )
    }
}
