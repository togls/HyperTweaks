package io.github.togls.hypertweaks.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.data.NavBarButton
import io.github.togls.hypertweaks.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.data.XposedConfigRepository
import io.github.togls.hypertweaks.service.XposedServiceStore
import io.github.togls.hypertweaks.ui.theme.HyperTweaksTheme

class SettingsActivity : ComponentActivity() {

    private val configRepository = XposedConfigRepository()

    private var uiState by mutableStateOf(SettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val service = XposedServiceStore.service.value

            LaunchedEffect(service) {
                loadConfig(service)
            }

            HyperTweaksTheme {
                SettingsScreen(
                    uiState = uiState,
                    onStartButtonChange = ::updateStartButton,
                    onEndButtonChange = ::updateEndButton,
                    onReloadClick = {
                        loadConfig(XposedServiceStore.service.value)
                    },
                )
            }
        }
    }

    private fun loadConfig(service: XposedService?) {
        if (service == null) {
            uiState = SettingsUiState(
                serviceConnected = false,
                config = uiState.config,
                message = getString(R.string.status_service_not_connected),
            )
            return
        }

        configRepository.loadConfig(service)
            .onSuccess { config ->
                uiState = SettingsUiState(
                    serviceConnected = true,
                    config = config,
                    message = getString(R.string.status_config_loaded),
                )
            }
            .onFailure { error ->
                uiState = SettingsUiState(
                    serviceConnected = true,
                    config = uiState.config,
                    message = error.message ?: getString(R.string.status_read_config_failed),
                )
            }
    }

    private fun updateStartButton(button: NavBarButton) {
        val nextConfig = uiState.config.copy(start = button)
        saveConfig(nextConfig)
    }

    private fun updateEndButton(button: NavBarButton) {
        val nextConfig = uiState.config.copy(end = button)
        saveConfig(nextConfig)
    }

    private fun saveConfig(config: NavBarLayoutConfig) {
        val service = XposedServiceStore.service.value

        if (service == null) {
            uiState = uiState.copy(
                serviceConnected = false,
                message = getString(R.string.status_save_without_service),
            )
            return
        }

        configRepository.saveConfig(service, config)
            .onSuccess { savedConfig ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    config = savedConfig,
                    message = getString(R.string.status_config_saved),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    message = error.message ?: getString(R.string.status_save_config_failed),
                )
            }
    }
}