package io.github.togls.hypertweaks.ui.settings

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.core.config.ConfigRepository
import io.github.togls.hypertweaks.core.config.FeatureToggles
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.core.config.XposedConfigRepository
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val configRepository: ConfigRepository = XposedConfigRepository()

    var uiState by mutableStateOf(SettingsUiState())
        private set

    fun onAction(
        action: SettingsAction,
    ) {
        when (action) {
            is SettingsAction.SetImeEnabled -> {
                updateImeEnabled(
                    enabled = action.enabled,
                )
            }

            is SettingsAction.SetKeepAliveEnabled -> {
                updateKeepAliveEnabled(
                    enabled = action.enabled,
                )
            }

            is SettingsAction.SetStartButton -> {
                updateStartButton(
                    button = action.button,
                )
            }

            is SettingsAction.SetEndButton -> {
                updateEndButton(
                    button = action.button,
                )
            }

            is SettingsAction.UpdateKeepAlivePackagesText -> {
                updateKeepAlivePackagesText(action.text)
            }

            SettingsAction.SaveKeepAlivePackages -> {
                saveKeepAlivePackages()
            }

            SettingsAction.ReloadConfig -> {
                loadConfig()
            }

            is SettingsAction.SetKeepAliveMode -> {
                updateKeepAliveMode(action.mode)
            }
        }
    }

    fun loadConfig() {

        val navConfigResult = configRepository.loadConfig()

        val keepAlivePackages = configRepository
            .loadKeepAlivePackages()
            .getOrDefault(emptySet())

        val featureToggles = configRepository
            .loadFeatureToggles()
            .getOrDefault(FeatureToggles())

        val keepAliveMode = configRepository
            .loadKeepAliveMode()
            .getOrDefault(KeepAliveMode.Default)

        navConfigResult
            .onSuccess { config ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    imeEnabled = featureToggles.imeEnabled,
                    keepAliveEnabled = featureToggles.keepAliveEnabled,
                    config = config,
                    keepAliveMode = keepAliveMode,
                    keepAlivePackagesText = KeepAlivePackages.format(keepAlivePackages),
                    invalidKeepAlivePackages = emptyList(),
                    message = string(R.string.status_config_loaded),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    imeEnabled = featureToggles.imeEnabled,
                    keepAliveMode = keepAliveMode,
                    keepAliveEnabled = featureToggles.keepAliveEnabled,
                    keepAlivePackagesText = KeepAlivePackages.format(keepAlivePackages),
                    invalidKeepAlivePackages = emptyList(),
                    message = error.message ?: string(R.string.status_read_config_failed),
                )
            }
    }

    private fun updateStartButton(
        button: NavBarButton,
    ) {
        val nextConfig = uiState.config.copy(start = button)
        saveConfig(
            config = nextConfig,
        )
    }

    private fun updateEndButton(
        button: NavBarButton,
    ) {
        val nextConfig = uiState.config.copy(end = button)
        saveConfig(
            config = nextConfig,
        )
    }

    private fun saveConfig(
        config: NavBarLayoutConfig,
    ) {

        configRepository
            .saveConfig(config)
            .onSuccess { savedConfig ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    config = savedConfig,
                    message = string(R.string.status_config_saved),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    message = error.message ?: string(R.string.status_save_config_failed),
                )
            }
    }

    private fun updateKeepAlivePackagesText(text: String) {
        val parseResult = KeepAlivePackages.parseWithInvalid(text)

        uiState = uiState.copy(
            keepAlivePackagesText = text,
            invalidKeepAlivePackages = parseResult.invalidValues,
        )
    }

    private fun saveKeepAlivePackages() {
        val parseResult = KeepAlivePackages.parseWithInvalid(uiState.keepAlivePackagesText)

        if (parseResult.invalidValues.isNotEmpty()) {
            uiState = uiState.copy(
                serviceConnected = true,
                invalidKeepAlivePackages = parseResult.invalidValues,
                message = string(
                    R.string.status_keep_alive_invalid_packages,
                    parseResult.invalidValues.joinToString(),
                ),
            )
            return
        }

        configRepository
            .saveKeepAlivePackages(parseResult.packages)
            .onSuccess { savedPackages ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    keepAlivePackagesText = KeepAlivePackages.format(savedPackages),
                    invalidKeepAlivePackages = emptyList(),
                    message = string(R.string.status_keep_alive_saved),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    message = error.message ?: string(R.string.status_save_config_failed),
                )
            }
    }

    private fun updateImeEnabled(
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            toggles = FeatureToggles(
                imeEnabled = enabled,
                keepAliveEnabled = uiState.keepAliveEnabled,
            ),
        )
    }

    private fun updateKeepAliveEnabled(
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            toggles = FeatureToggles(
                imeEnabled = uiState.imeEnabled,
                keepAliveEnabled = enabled,
            ),
        )
    }

    private fun saveFeatureToggles(
        toggles: FeatureToggles,
    ) {
        configRepository
            .saveFeatureToggles(toggles)
            .onSuccess { savedToggles ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    imeEnabled = savedToggles.imeEnabled,
                    keepAliveEnabled = savedToggles.keepAliveEnabled,
                    message = string(R.string.status_feature_toggle_saved),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    message = error.message ?: string(R.string.status_save_config_failed),
                )
            }
    }

    private fun updateKeepAliveMode(mode: KeepAliveMode) {
        if (!configRepository.serviceConnected) {
            uiState = uiState.copy(
                serviceConnected = false,
                message = string(R.string.status_save_without_service),
            )
            return
        }

        configRepository
            .saveKeepAliveMode(mode)
            .onSuccess { savedMode ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    keepAliveMode = savedMode,
                    message = string(R.string.status_keep_alive_mode_saved),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = configRepository.serviceConnected,
                    message = error.message ?: string(R.string.status_save_config_failed),
                )
            }
    }

    private fun string(
        @StringRes resId: Int,
        vararg args: Any,
    ): String {
        val app = getApplication<Application>()

        return if (args.isEmpty()) {
            app.getString(resId)
        } else {
            app.getString(resId, *args)
        }
    }
}