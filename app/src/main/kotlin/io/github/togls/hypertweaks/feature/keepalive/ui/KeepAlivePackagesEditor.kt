package io.github.togls.hypertweaks.feature.keepalive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.R

@Composable
fun KeepAlivePackagesEditor(
    packagesText: String,
    invalidPackages: List<String>,
    enabled: Boolean,
    onPackagesTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.keep_alive_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = stringResource(R.string.keep_alive_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = packagesText,
            enabled = enabled,
            onValueChange = onPackagesTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            isError = invalidPackages.isNotEmpty(),
            label = {
                Text(text = stringResource(R.string.keep_alive_packages_label))
            },
            placeholder = {
                Text(
                    text = "org.mozilla.firefox\norg.mozilla.firefox_beta\norg.mozilla.fenix",
                )
            },
            supportingText = {
                if (invalidPackages.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.keep_alive_invalid_packages_hint,
                            invalidPackages.joinToString(),
                        ),
                    )
                }
            },
        )

        Button(
            enabled = enabled,
            onClick = {
                focusManager.clearFocus()
                onSaveClick()
            },
        ) {
            Text(text = stringResource(R.string.action_save_keep_alive_packages))
        }
    }
}