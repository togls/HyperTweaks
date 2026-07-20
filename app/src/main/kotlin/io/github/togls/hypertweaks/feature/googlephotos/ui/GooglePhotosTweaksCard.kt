package io.github.togls.hypertweaks.feature.googlephotos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.ui.components.AppPreferenceGroup
import io.github.togls.hypertweaks.ui.components.AppSwitchPreference

@Composable
fun GooglePhotosTweaksCard(
    uiState: GooglePhotosSettingsUiState,
    onLocationEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPreferenceGroup(
        modifier = modifier,
    ) {
        AppSwitchPreference(
            title = stringResource(R.string.feature_google_photos_location_title),
            summary = stringResource(R.string.feature_google_photos_location_description),
            checked = uiState.enabled,
            enabled = true,
            onCheckedChange = onLocationEnabledChange,
        )
    }
}
