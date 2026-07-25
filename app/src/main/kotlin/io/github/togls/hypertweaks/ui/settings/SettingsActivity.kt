package io.github.togls.hypertweaks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.togls.hypertweaks.feature.logviewer.LogViewerRoute
import io.github.togls.hypertweaks.service.XposedServiceStore
import io.github.togls.hypertweaks.ui.diagnostics.HookDiagnosticsRoute
import io.github.togls.hypertweaks.ui.theme.HyperTweaksMiuixTheme
import io.github.togls.hypertweaks.ui.theme.HyperTweaksTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperTweaksMiuixTheme {
                HyperTweaksTheme {
                    SettingsNavigation()
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigation(
    viewModel: SettingsViewModel = viewModel(),
) {
    val service = XposedServiceStore.service.value
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.SETTINGS) }
    LaunchedEffect(service) { viewModel.loadConfig() }
    BackHandler(enabled = destination != SettingsDestination.SETTINGS) {
        destination = SettingsDestination.SETTINGS
    }
    when (destination) {
        SettingsDestination.SETTINGS -> SettingsRoute(
            viewModel = viewModel,
            onOpenLogs = { destination = SettingsDestination.LOGS },
            onOpenDiagnostics = { destination = SettingsDestination.DIAGNOSTICS },
        )

        SettingsDestination.LOGS -> LogViewerRoute(
            onBack = { destination = SettingsDestination.SETTINGS },
        )

        SettingsDestination.DIAGNOSTICS -> HookDiagnosticsRoute(
            onBack = { destination = SettingsDestination.SETTINGS },
        )
    }
}

@Composable
private fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    SettingsScreen(
        uiState = viewModel.uiState,
        callbacks = settingsCallbacks(viewModel, onOpenLogs, onOpenDiagnostics),
    )
}

private fun settingsCallbacks(
    viewModel: SettingsViewModel,
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
): SettingsCallbacks {
    return SettingsCallbacks(
        onLogModeChange = { viewModel.onAction(SettingsAction.SetLogMode(it)) },
        onViewLogsClick = onOpenLogs,
        onViewDiagnosticsClick = onOpenDiagnostics,
        onSystemServerFeaturesEnabledChange = {
            viewModel.onAction(SettingsAction.SetSystemServerFeaturesEnabled(it))
        },
        onImeEnabledChange = { viewModel.onAction(SettingsAction.SetImeEnabled(it)) },
        onGooglePhotosLocationEnabledChange = {
            viewModel.onAction(SettingsAction.SetGooglePhotosLocationEnabled(it))
        },
        onKeepAliveEnabledChange = { viewModel.onAction(SettingsAction.SetKeepAliveEnabled(it)) },
        onStartButtonChange = { viewModel.onAction(SettingsAction.SetStartButton(it)) },
        onEndButtonChange = { viewModel.onAction(SettingsAction.SetEndButton(it)) },
        onKeepAliveModeChange = { viewModel.onAction(SettingsAction.SetKeepAliveMode(it)) },
        onKeepAlivePackagesTextChange = {
            viewModel.onAction(SettingsAction.UpdateKeepAlivePackagesText(it))
        },
        onSaveKeepAlivePackagesClick = { viewModel.onAction(SettingsAction.SaveKeepAlivePackages) },
        onReloadClick = { viewModel.onAction(SettingsAction.ReloadConfig) },
    )
}

private enum class SettingsDestination {
    SETTINGS,
    LOGS,
    DIAGNOSTICS,
}
