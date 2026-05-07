package io.github.togls.hypertweaks.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.BuildConfig
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onImeEnabledChange: (Boolean) -> Unit,
    onKeepAliveEnabledChange: (Boolean) -> Unit,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onKeepAliveModeChange: (KeepAliveMode) -> Unit,
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
                onImeEnabledChange = onImeEnabledChange,
                onKeepAliveEnabledChange = onKeepAliveEnabledChange,
                onStartButtonChange = onStartButtonChange,
                onEndButtonChange = onEndButtonChange,
                onKeepAliveModeChange = onKeepAliveModeChange,
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
    onImeEnabledChange: (Boolean) -> Unit,
    onKeepAliveEnabledChange: (Boolean) -> Unit,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onKeepAliveModeChange: (KeepAliveMode) -> Unit,
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

        ImeTweaksCard(
            imeEnabled = uiState.imeEnabled,
            serviceConnected = uiState.serviceConnected,
            config = uiState.config,
            showDebugInfo = showDebugInfo,
            onImeEnabledChange = onImeEnabledChange,
            onStartButtonChange = onStartButtonChange,
            onEndButtonChange = onEndButtonChange,
        )

        KeepAliveTweaksCard(
            connected = uiState.serviceConnected,
            enabled = uiState.keepAliveEnabled,
            mode = uiState.keepAliveMode,
            onKeepAliveEnabledChange = onKeepAliveEnabledChange,
            onKeepAliveModeChange = onKeepAliveModeChange,
            packagesText = uiState.keepAlivePackagesText,
            invalidPackages = uiState.invalidKeepAlivePackages,
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
private fun ImeTweaksCard(
    imeEnabled: Boolean,
    serviceConnected: Boolean,
    config: NavBarLayoutConfig,
    showDebugInfo: Boolean,
    onImeEnabledChange: (Boolean) -> Unit,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
) {
    val controlsEnabled = serviceConnected && imeEnabled

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FeatureSwitchRow(
                title = stringResource(R.string.feature_ime_title),
                description = stringResource(R.string.feature_ime_description),
                checked = imeEnabled,
                enabled = serviceConnected,
                onCheckedChange = onImeEnabledChange,
            )

            HorizontalDivider()

            Column(
                modifier = Modifier.alpha(if (imeEnabled) 1f else 0.5f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NavBarButtonSelectorContent(
                    title = stringResource(R.string.start_button_title),
                    description = stringResource(R.string.start_button_description),
                    selected = config.start,
                    enabled = controlsEnabled,
                    onSelectedChange = onStartButtonChange,
                )

                NavBarButtonSelectorContent(
                    title = stringResource(R.string.end_button_title),
                    description = stringResource(R.string.end_button_description),
                    selected = config.end,
                    enabled = controlsEnabled,
                    onSelectedChange = onEndButtonChange,
                )

                if (showDebugInfo) {
                    HorizontalDivider()

                    HandlePreviewContent(
                        handleLayout = config.toHandleLayout(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun NavBarButtonSelectorContent(
    title: String,
    description: String,
    selected: NavBarButton,
    enabled: Boolean,
    onSelectedChange: (NavBarButton) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
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

@Composable
private fun HandlePreviewContent(
    handleLayout: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.handle_preview_title),
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = handleLayout,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeepAliveTweaksCard(
    connected: Boolean,
    enabled: Boolean,
    mode: KeepAliveMode,
    onKeepAliveEnabledChange: (Boolean) -> Unit,
    onKeepAliveModeChange: (KeepAliveMode) -> Unit,
    packagesText: String,
    invalidPackages: List<String>,
    onPackagesTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FeatureSwitchRow(
                title = stringResource(R.string.feature_keep_alive_title),
                description = stringResource(R.string.feature_keep_alive_description),
                checked = enabled,
                enabled = connected,
                onCheckedChange = onKeepAliveEnabledChange,
            )

            HorizontalDivider()

            KeepAliveModeSelector(
                selectedMode = mode,
                enabled = connected && enabled,
                onModeChange = onKeepAliveModeChange,
            )

            KeepAlivePackagesContent(
                packagesText = packagesText,
                invalidPackages = invalidPackages,
                enabled = connected,
                onPackagesTextChange = onPackagesTextChange,
                onSaveClick = onSaveClick,
            )
        }
    }
}

@Composable
private fun KeepAliveModeSelector(
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

@Composable
private fun KeepAlivePackagesContent(
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
