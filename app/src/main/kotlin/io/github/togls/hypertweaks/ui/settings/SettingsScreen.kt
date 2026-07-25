package io.github.togls.hypertweaks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.BuildConfig
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.feature.googlephotos.ui.GooglePhotosTweaksCard
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.ime.ui.ImeTweaksCard
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.feature.keepalive.ui.KeepAliveTweaksCard
import io.github.togls.hypertweaks.ui.components.AppScaffold
import io.github.togls.hypertweaks.ui.components.AppSpacing

internal data class SettingsCallbacks(
    val onLogModeChange: (LogMode) -> Unit,
    val onViewLogsClick: () -> Unit,
    val onViewDiagnosticsClick: () -> Unit,
    val onSystemServerFeaturesEnabledChange: (Boolean) -> Unit,
    val onImeEnabledChange: (Boolean) -> Unit,
    val onGooglePhotosLocationEnabledChange: (Boolean) -> Unit,
    val onKeepAliveEnabledChange: (Boolean) -> Unit,
    val onStartButtonChange: (NavBarButton) -> Unit,
    val onEndButtonChange: (NavBarButton) -> Unit,
    val onKeepAliveModeChange: (KeepAliveMode) -> Unit,
    val onKeepAlivePackagesTextChange: (String) -> Unit,
    val onSaveKeepAlivePackagesClick: () -> Unit,
    val onReloadClick: () -> Unit,
)

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
    showDebugInfo: Boolean = BuildConfig.DEBUG,
) {
    AppScaffold(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
        modifier = modifier,
    ) {
        SettingsContent(
            uiState = uiState,
            callbacks = callbacks,
            contentPadding = it,
            showDebugInfo = showDebugInfo,
        )
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
    contentPadding: PaddingValues,
    showDebugInfo: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .padding(
                    start = AppSpacing.large,
                    top = AppSpacing.small,
                    end = AppSpacing.large,
                    bottom = AppSpacing.extraLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
        ) {
            SettingsSections(
                uiState = uiState,
                callbacks = callbacks,
                showDebugInfo = showDebugInfo,
            )
        }
    }
}

@Composable
private fun SettingsSections(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
    showDebugInfo: Boolean,
) {
    ServiceStateCard(
        serviceConnected = uiState.service.connected,
        message = uiState.service.message,
        systemServerFeaturesEnabled = uiState.service.systemServerFeaturesEnabled,
        onSystemServerFeaturesEnabledChange = callbacks.onSystemServerFeaturesEnabledChange,
        onReloadClick = callbacks.onReloadClick,
        showDebugInfo = showDebugInfo,
    )
    LogSettingsCard(
        mode = uiState.logging.mode,
        serviceConnected = uiState.service.connected,
        onModeChange = callbacks.onLogModeChange,
        onViewLogsClick = callbacks.onViewLogsClick,
        onViewDiagnosticsClick = callbacks.onViewDiagnosticsClick,
    )
    ImeTweaksCard(
        serviceConnected = uiState.service.connected,
        uiState = uiState.ime,
        showDebugInfo = showDebugInfo,
        onImeEnabledChange = callbacks.onImeEnabledChange,
        onStartButtonChange = callbacks.onStartButtonChange,
        onEndButtonChange = callbacks.onEndButtonChange,
    )
    GooglePhotosTweaksCard(
        uiState = uiState.googlePhotos,
        onLocationEnabledChange = callbacks.onGooglePhotosLocationEnabledChange,
    )
    KeepAliveTweaksCard(
        serviceConnected = uiState.service.connected,
        uiState = uiState.keepAlive,
        onKeepAliveEnabledChange = callbacks.onKeepAliveEnabledChange,
        onKeepAliveModeChange = callbacks.onKeepAliveModeChange,
        onPackagesTextChange = callbacks.onKeepAlivePackagesTextChange,
        onSaveClick = callbacks.onSaveKeepAlivePackagesClick,
    )
}
