package io.github.togls.hypertweaks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.BuildConfig
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.data.NavBarButton

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onKeepAlivePackagesTextChange: (String) -> Unit,
    onSaveKeepAlivePackagesClick: () -> Unit,
    onReloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDebugInfo: Boolean = BuildConfig.DEBUG,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold { innerPadding ->
            SettingsContent(
                uiState = uiState,
                onStartButtonChange = onStartButtonChange,
                onEndButtonChange = onEndButtonChange,
                onKeepAlivePackagesTextChange = onKeepAlivePackagesTextChange,
                onSaveKeepAlivePackagesClick = onSaveKeepAlivePackagesClick,
                onReloadClick = onReloadClick,
                contentPadding = innerPadding,
                showDebugInfo = showDebugInfo,
            )
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onKeepAlivePackagesTextChange: (String) -> Unit,
    onSaveKeepAlivePackagesClick: () -> Unit,
    onReloadClick: () -> Unit,
    contentPadding: PaddingValues,
    showDebugInfo: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ServiceStateCard(
            serviceConnected = uiState.serviceConnected,
            message = uiState.message,
            onReloadClick = onReloadClick,
            showDebugInfo = showDebugInfo,
        )

        NavBarButtonSelector(
            title = stringResource(R.string.start_button_title),
            description = stringResource(R.string.start_button_description),
            selected = uiState.config.start,
            enabled = uiState.serviceConnected,
            onSelectedChange = onStartButtonChange,
        )

        NavBarButtonSelector(
            title = stringResource(R.string.end_button_title),
            description = stringResource(R.string.end_button_description),
            selected = uiState.config.end,
            enabled = uiState.serviceConnected,
            onSelectedChange = onEndButtonChange,
        )

        if (showDebugInfo) {
            HandlePreviewCard(
                handleLayout = uiState.config.toHandleLayout(),
            )
        }

        KeepAlivePackagesCard(
            packagesText = uiState.keepAlivePackagesText,
            invalidPackages = uiState.invalidKeepAlivePackages,
            enabled = uiState.serviceConnected,
            onPackagesTextChange = onKeepAlivePackagesTextChange,
            onSaveClick = onSaveKeepAlivePackagesClick,
        )
    }
}

@Composable
private fun ServiceStateCard(
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

@Composable
private fun NavBarButtonSelector(
    title: String,
    description: String,
    selected: NavBarButton,
    enabled: Boolean,
    onSelectedChange: (NavBarButton) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.padding(horizontal = 6.dp))

                Column {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { expanded = true },
                    ) {
                        Text(text = stringResource(selected.displayNameRes))
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        NavBarButton.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(option.displayNameRes))
                                },
                                onClick = {
                                    expanded = false
                                    onSelectedChange(option)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandlePreviewCard(
    handleLayout: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.handle_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = handleLayout,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeepAlivePackagesCard(
    packagesText: String,
    invalidPackages: List<String>,
    enabled: Boolean,
    onPackagesTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
}