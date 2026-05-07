package io.github.togls.hypertweaks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.R

@Composable
fun ServiceStateCard(
    serviceConnected: Boolean,
    message: String,
    onReloadClick: () -> Unit,
    showDebugInfo: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (serviceConnected) {
                    stringResource(R.string.xposed_service_connected)
                } else {
                    stringResource(R.string.xposed_service_disconnected)
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (!showDebugInfo) {
                return@Card
            }

            Text(
                text = message.ifBlank {
                    stringResource(R.string.status_waiting_service)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onReloadClick,
            ) {
                Text(text = stringResource(R.string.action_reload_config))
            }
        }
    }
}