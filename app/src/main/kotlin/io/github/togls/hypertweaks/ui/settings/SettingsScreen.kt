package io.github.togls.hypertweaks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.BuildConfig
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.ime.ui.ImeTweaksCard
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.ui.KeepAliveTweaksCard
import io.github.togls.hypertweaks.ui.component.FeatureSwitchRow
import io.github.togls.hypertweaks.ui.component.SettingsSectionCard

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
