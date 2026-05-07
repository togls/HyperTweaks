package io.github.togls.hypertweaks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.togls.hypertweaks.service.XposedServiceStore
import io.github.togls.hypertweaks.ui.theme.HyperTweaksTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val service = XposedServiceStore.service.value

            LaunchedEffect(service) {
                viewModel.loadConfig()
            }

            HyperTweaksTheme {
                SettingsScreen(
                    uiState = viewModel.uiState,
                    onImeEnabledChange = { enabled ->
                        viewModel.onAction(SettingsAction.SetImeEnabled(enabled))
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