package io.github.togls.hypertweaks.feature.keepalive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.ui.components.AppInfoPreference
import io.github.togls.hypertweaks.ui.components.AppRadioPreference

@Composable
fun KeepAliveModeSelector(
    selectedMode: KeepAliveMode,
    enabled: Boolean,
    onModeChange: (KeepAliveMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        AppInfoPreference(
            title = stringResource(R.string.keep_alive_mode_title),
        )

        AppRadioPreference(
            title = stringResource(R.string.keep_alive_mode_oom_only_title),
            summary = stringResource(R.string.keep_alive_mode_oom_only_description),
            selected = selectedMode == KeepAliveMode.OomOnly,
            enabled = enabled,
            onClick = { onModeChange(KeepAliveMode.OomOnly) },
        )

        AppRadioPreference(
            title = stringResource(R.string.keep_alive_mode_conservative_title),
            summary = stringResource(R.string.keep_alive_mode_conservative_description),
            selected = selectedMode == KeepAliveMode.Conservative,
            enabled = enabled,
            onClick = { onModeChange(KeepAliveMode.Conservative) },
        )

        AppRadioPreference(
            title = stringResource(R.string.keep_alive_mode_aggressive_title),
            summary = stringResource(R.string.keep_alive_mode_aggressive_description),
            selected = selectedMode == KeepAliveMode.Aggressive,
            enabled = enabled,
            onClick = { onModeChange(KeepAliveMode.Aggressive) },
        )
    }
}
