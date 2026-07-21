package io.github.togls.hypertweaks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.togls.hypertweaks.service.XposedServiceStore
import io.github.togls.hypertweaks.feature.logviewer.LogViewerRoute
import io.github.togls.hypertweaks.ui.theme.HyperTweaksMiuixTheme
import io.github.togls.hypertweaks.ui.theme.HyperTweaksTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val service = XposedServiceStore.service.value
            var showLogViewer by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(service) {
                viewModel.loadConfig()
            }

            HyperTweaksMiuixTheme {
                HyperTweaksTheme {
                    BackHandler(enabled = showLogViewer) { showLogViewer = false }
                    if (showLogViewer) {
                        LogViewerRoute(onBack = { showLogViewer = false })
                    } else {
                        SettingsScreen(
                        uiState = viewModel.uiState,
                        onLogModeChange = { mode ->
                            viewModel.onAction(SettingsAction.SetLogMode(mode))
                        },
                        onViewLogsClick = { showLogViewer = true },
                        onImeEnabledChange = { enabled ->
                            viewModel.onAction(SettingsAction.SetImeEnabled(enabled))
                        },
                        onGooglePhotosLocationEnabledChange = { enabled ->
                            viewModel.onAction(SettingsAction.SetGooglePhotosLocationEnabled(enabled))
                        },
                        onKeepAliveEnabledChange = { enabled ->
                            viewModel.onAction(SettingsAction.SetKeepAliveEnabled(enabled))
                        },
                        onStartButtonChange = { button ->
                            viewModel.onAction(SettingsAction.SetStartButton(button))
                        },
                        onEndButtonChange = { button ->
                            viewModel.onAction(SettingsAction.SetEndButton(button))
                        },
                        onKeepAliveModeChange = { mode ->
                            viewModel.onAction(SettingsAction.SetKeepAliveMode(mode))
                        },
                        onKeepAlivePackagesTextChange = { text ->
                            viewModel.onAction(SettingsAction.UpdateKeepAlivePackagesText(text))
                        },
                        onSaveKeepAlivePackagesClick = {
                            viewModel.onAction(SettingsAction.SaveKeepAlivePackages)
                        },
                        onReloadClick = {
                            viewModel.onAction(SettingsAction.ReloadConfig)
                        },
                    )
                    }
                }
            }
        }
    }
}
