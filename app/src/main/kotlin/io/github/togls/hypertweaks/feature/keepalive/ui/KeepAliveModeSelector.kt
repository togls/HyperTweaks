package io.github.togls.hypertweaks.feature.keepalive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode

@Composable
fun KeepAliveModeSelector(
    selectedMode: KeepAliveMode,
    enabled: Boolean,
    onModeChange: (KeepAliveMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.keep_alive_mode_title),
            style = MaterialTheme.typography.titleMedium,
        )

        KeepAliveModeOption(
            title = stringResource(R.string.keep_alive_mode_conservative_title),
            description = stringResource(R.string.keep_alive_mode_conservative_description),
            selected = selectedMode == KeepAliveMode.Conservative,
            enabled = enabled,
            onClick = { onModeChange(KeepAliveMode.Conservative) },
        )

        KeepAliveModeOption(
            title = stringResource(R.string.keep_alive_mode_aggressive_title),
            description = stringResource(R.string.keep_alive_mode_aggressive_description),
            selected = selectedMode == KeepAliveMode.Aggressive,
            enabled = enabled,
            onClick = { onModeChange(KeepAliveMode.Aggressive) },
        )

        KeepAliveModeOption(
            title = stringResource(R.string.keep_alive_mode_oom_only_title),
            description = stringResource(R.string.keep_alive_mode_oom_only_description),
            selected = selectedMode == KeepAliveMode.OomOnly,
            enabled = enabled,
            onClick = { onModeChange(KeepAliveMode.OomOnly) },
        )
    }
}

@Composable
private fun KeepAliveModeOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        tonalElevation = if (selected) 2.dp else 0.dp,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}