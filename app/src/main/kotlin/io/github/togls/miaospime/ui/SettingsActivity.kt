package io.github.togls.miaospime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.togls.miaospime.data.NavBarButton
import io.github.togls.miaospime.data.NavBarLayoutConfig
import io.github.togls.miaospime.data.XposedConfigRepository
import io.github.togls.miaospime.service.XposedServiceStore
import io.github.togls.miaospime.ui.theme.MiAospImeTheme

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

            MiAospImeTheme {
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
                message = "Xposed Service 未连接，请确认模块已启用",
            )
            return
        }

        configRepository.loadConfig(service)
            .onSuccess { config ->
                uiState = SettingsUiState(
                    serviceConnected = true,
                    config = config,
                    message = "配置已从 remote preferences 加载",
                )
            }
            .onFailure { error ->
                uiState = SettingsUiState(
                    serviceConnected = true,
                    config = uiState.config,
                    message = error.message ?: "读取 remote preferences 失败",
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
                message = "Xposed Service 未连接，无法保存配置",
            )
            return
        }

        configRepository.saveConfig(service, config)
            .onSuccess { savedConfig ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    config = savedConfig,
                    message = "配置已保存到 remote preferences",
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    message = error.message ?: "保存 remote preferences 失败",
                )
            }
    }
}