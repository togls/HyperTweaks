package io.github.togls.hypertweaks.feature.ime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.ui.components.AppInfoPreference

@Composable
fun HandlePreviewCard(
    handleLayout: String,
    modifier: Modifier = Modifier,
) {
    AppInfoPreference(
        title = stringResource(R.string.handle_preview_title),
        summary = handleLayout,
        modifier = modifier,
    )
}
