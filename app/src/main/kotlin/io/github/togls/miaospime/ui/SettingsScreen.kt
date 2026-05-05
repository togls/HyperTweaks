package io.github.togls.miaospime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import io.github.togls.miaospime.data.NavBarButton

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onReloadClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                onReloadClick = onReloadClick,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onReloadClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Mi AOSP IME Kotlin",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = "Kotlin + Compose + libxposed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ServiceStateCard(
            serviceConnected = uiState.serviceConnected,
            message = uiState.message,
            onReloadClick = onReloadClick,
        )

        NavBarButtonSelector(
            title = "Start Button",
            description = "输入法导航栏左侧按钮",
            selected = uiState.config.start,
            enabled = uiState.serviceConnected,
            onSelectedChange = onStartButtonChange,
        )

        NavBarButtonSelector(
            title = "End Button",
            description = "输入法导航栏右侧按钮",
            selected = uiState.config.end,
            enabled = uiState.serviceConnected,
            onSelectedChange = onEndButtonChange,
        )

        HandlePreviewCard(
            handleLayout = uiState.config.toHandleLayout(),
        )
    }
}

@Composable
private fun ServiceStateCard(
    serviceConnected: Boolean,
    message: String,
    onReloadClick: () -> Unit,
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
                    "Xposed Service 已连接"
                } else {
                    "Xposed Service 未连接"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onReloadClick,
            ) {
                Text(text = "重新加载配置")
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
                        Text(text = selected.displayName)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        NavBarButton.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(text = option.displayName)
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

            HorizontalDivider()

            Text(
                text = "当前值：${selected.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                text = "nav_bar_layout_handle 预览",
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