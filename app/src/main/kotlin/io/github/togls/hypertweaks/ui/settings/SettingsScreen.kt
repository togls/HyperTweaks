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

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLogModeChange: (LogMode) -> Unit,
    onViewLogsClick: () -> Unit,
    onImeEnabledChange: (Boolean) -> Unit,
    onGooglePhotosLocationEnabledChange: (Boolean) -> Unit,
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
    AppScaffold(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
        modifier = modifier,
    ) {
        SettingsContent(
            uiState = uiState,
            onLogModeChange = onLogModeChange,
            onViewLogsClick = onViewLogsClick,
            onImeEnabledChange = onImeEnabledChange,
            onGooglePhotosLocationEnabledChange = onGooglePhotosLocationEnabledChange,
            onKeepAliveEnabledChange = onKeepAliveEnabledChange,
            onStartButtonChange = onStartButtonChange,
            onEndButtonChange = onEndButtonChange,
            onKeepAliveModeChange = onKeepAliveModeChange,
            onKeepAlivePackagesTextChange = onKeepAlivePackagesTextChange,
            onSaveKeepAlivePackagesClick = onSaveKeepAlivePackagesClick,
            onReloadClick = onReloadClick,
            contentPadding = it,
            showDebugInfo = showDebugInfo,
        )
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onLogModeChange: (LogMode) -> Unit,
    onViewLogsClick: () -> Unit,
    onImeEnabledChange: (Boolean) -> Unit,
    onGooglePhotosLocationEnabledChange: (Boolean) -> Unit,
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
                onLogModeChange = onLogModeChange,
                onViewLogsClick = onViewLogsClick,
                onImeEnabledChange = onImeEnabledChange,
                onGooglePhotosLocationEnabledChange = onGooglePhotosLocationEnabledChange,
                onKeepAliveEnabledChange = onKeepAliveEnabledChange,
                onStartButtonChange = onStartButtonChange,
                onEndButtonChange = onEndButtonChange,
                onKeepAliveModeChange = onKeepAliveModeChange,
                onKeepAlivePackagesTextChange = onKeepAlivePackagesTextChange,
                onSaveKeepAlivePackagesClick = onSaveKeepAlivePackagesClick,
                onReloadClick = onReloadClick,
                showDebugInfo = showDebugInfo,
            )
        }
    }
}

@Composable
private fun SettingsSections(
    uiState: SettingsUiState,
    onLogModeChange: (LogMode) -> Unit,
    onViewLogsClick: () -> Unit,
    onImeEnabledChange: (Boolean) -> Unit,
    onGooglePhotosLocationEnabledChange: (Boolean) -> Unit,
    onKeepAliveEnabledChange: (Boolean) -> Unit,
    onStartButtonChange: (NavBarButton) -> Unit,
    onEndButtonChange: (NavBarButton) -> Unit,
    onKeepAliveModeChange: (KeepAliveMode) -> Unit,
    onKeepAlivePackagesTextChange: (String) -> Unit,
    onSaveKeepAlivePackagesClick: () -> Unit,
    onReloadClick: () -> Unit,
    showDebugInfo: Boolean,
) {
    ServiceStateCard(
        serviceConnected = uiState.service.connected,
        message = uiState.service.message,
        onReloadClick = onReloadClick,
        showDebugInfo = showDebugInfo,
    )
    LogSettingsCard(
        mode = uiState.logging.mode,
        serviceConnected = uiState.service.connected,
        onModeChange = onLogModeChange,
        onViewLogsClick = onViewLogsClick,
    )
    ImeTweaksCard(
        serviceConnected = uiState.service.connected,
        uiState = uiState.ime,
        showDebugInfo = showDebugInfo,
        onImeEnabledChange = onImeEnabledChange,
        onStartButtonChange = onStartButtonChange,
        onEndButtonChange = onEndButtonChange,
    )
    GooglePhotosTweaksCard(
        uiState = uiState.googlePhotos,
        onLocationEnabledChange = onGooglePhotosLocationEnabledChange,
    )
    KeepAliveTweaksCard(
        serviceConnected = uiState.service.connected,
        uiState = uiState.keepAlive,
        onKeepAliveEnabledChange = onKeepAliveEnabledChange,
        onKeepAliveModeChange = onKeepAliveModeChange,
        onPackagesTextChange = onKeepAlivePackagesTextChange,
        onSaveClick = onSaveKeepAlivePackagesClick,
    )
}
