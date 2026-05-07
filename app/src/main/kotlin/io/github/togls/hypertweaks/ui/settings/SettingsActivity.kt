package io.github.togls.hypertweaks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.togls.hypertweaks.data.XposedConfigRepository
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
                viewModel.loadConfig(service)
            }

            HyperTweaksTheme {
                SettingsScreen(
                    uiState = viewModel.uiState,
                    onImeEnabledChange = { enabled ->
                        viewModel.onAction(
                            action = SettingsAction.SetImeEnabled(enabled),
                            service = XposedServiceStore.service.value,
                        )
                    },
                    onKeepAliveEnabledChange = { enabled ->
                        viewModel.onAction(
                            action = SettingsAction.SetKeepAliveEnabled(enabled),
                            service = XposedServiceStore.service.value,
                        )
                    },
                    onStartButtonChange = { button ->
                        viewModel.onAction(
                            action = SettingsAction.SetStartButton(button),
                            service = XposedServiceStore.service.value,
                        )
                    },
                    onEndButtonChange = { button ->
                        viewModel.onAction(
                            action = SettingsAction.SetEndButton(button),
                            service = XposedServiceStore.service.value,
                        )
                    },
                    onKeepAlivePackagesTextChange = { text ->
                        viewModel.onAction(
                            action = SettingsAction.UpdateKeepAlivePackagesText(text),
                            service = XposedServiceStore.service.value,
                        )
                    },
                    onSaveKeepAlivePackagesClick = {
                        viewModel.onAction(
                            action = SettingsAction.SaveKeepAlivePackages,
                            service = XposedServiceStore.service.value,
                        )
                    },
                    onReloadClick = {
                        viewModel.onAction(
                            action = SettingsAction.ReloadConfig,
                            service = XposedServiceStore.service.value
                        )
                    },
                )
            }
        }
    }
}