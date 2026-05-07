package io.github.togls.hypertweaks.ui.settings

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.libxposed.service.XposedService
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.data.FeatureToggles
import io.github.togls.hypertweaks.data.KeepAlivePackages
import io.github.togls.hypertweaks.data.NavBarButton
import io.github.togls.hypertweaks.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.data.XposedConfigRepository

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val configRepository = XposedConfigRepository()

    var uiState by mutableStateOf(SettingsUiState())
        private set

    fun onAction(
        action: SettingsAction,
        service: XposedService?,
    ) {
        when (action) {
            is SettingsAction.SetImeEnabled -> {
                updateImeEnabled(
                    service = service,
                    enabled = action.enabled,
                )
            }

            is SettingsAction.SetKeepAliveEnabled -> {
                updateKeepAliveEnabled(
                    service = service,
                    enabled = action.enabled,
                )
            }

            is SettingsAction.SetStartButton -> {
                updateStartButton(
                    service = service,
                    button = action.button,
                )
            }

            is SettingsAction.SetEndButton -> {
                updateEndButton(
                    service = service,
                    button = action.button,
                )
            }

            is SettingsAction.UpdateKeepAlivePackagesText -> {
                updateKeepAlivePackagesText(action.text)
            }

            SettingsAction.SaveKeepAlivePackages -> {
                saveKeepAlivePackages(service)
            }

            SettingsAction.ReloadConfig -> {
                loadConfig(service)
            }
        }
    }

    fun loadConfig(service: XposedService?) {
        if (service == null) {
            uiState = uiState.copy(
                serviceConnected = false,
                message = string(R.string.status_service_not_connected),
            )
            return
        }

        val navConfigResult = configRepository.loadConfig(service)

        val keepAlivePackages = configRepository
            .loadKeepAlivePackages(service)
            .getOrDefault(emptySet())

        val featureToggles = configRepository
            .loadFeatureToggles(service)
            .getOrDefault(FeatureToggles())

        navConfigResult
            .onSuccess { config ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    imeEnabled = featureToggles.imeEnabled,
                    keepAliveEnabled = featureToggles.keepAliveEnabled,
                    config = config,
                    keepAlivePackagesText = KeepAlivePackages.format(keepAlivePackages),
                    invalidKeepAlivePackages = emptyList(),
                    message = string(R.string.status_config_loaded),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    serviceConnected = true,
                    imeEnabled = featureToggles.imeEnabled,
                    keepAliveEnabled = featureToggles.keepAliveEnabled,
                    keepAlivePackagesText = KeepAlivePackages.format(keepAlivePackages),
                    invalidKeepAlivePackages = emptyList(),
                    message = error.message ?: string(R.string.status_read_config_failed),
                )
            }
    }

    private fun updateStartButton(
        service: XposedService?,
        button: NavBarButton,
    ) {
        val nextConfig = uiState.config.copy(start = button)
        saveConfig(
            service = service,
            config = nextConfig,
        )
    }

    private fun updateEndButton(
        service: XposedService?,
        button: NavBarButton,
    ) {
        val nextConfig = uiState.config.copy(end = button)
        saveConfig(
            service = service,
            config = nextConfig,
        )
    }

    private fun saveConfig(
        service: XposedService?,
        config: NavBarLayoutConfig,
    ) {
        if (service == null) {
            uiState = uiState.copy(
                serviceConnected = false,
                message = string(R.string.status_save_without_service),
            )
            return
        }

        configRepository
            .saveConfig(service, config)
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

    private fun saveKeepAlivePackages(service: XposedService?) {
        if (service == null) {
            uiState = uiState.copy(
                serviceConnected = false,
                message = string(R.string.status_save_without_service),
            )
            return
        }

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
            .saveKeepAlivePackages(service, parseResult.packages)
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
        service: XposedService?,
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            service = service,
            toggles = FeatureToggles(
                imeEnabled = enabled,
                keepAliveEnabled = uiState.keepAliveEnabled,
            ),
        )
    }

    private fun updateKeepAliveEnabled(
        service: XposedService?,
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            service = service,
            toggles = FeatureToggles(
                imeEnabled = uiState.imeEnabled,
                keepAliveEnabled = enabled,
            ),
        )
    }

    private fun saveFeatureToggles(
        service: XposedService?,
        toggles: FeatureToggles,
    ) {
        if (service == null) {
            uiState = uiState.copy(
                serviceConnected = false,
                message = string(R.string.status_save_without_service),
            )
            return
        }

        configRepository
            .saveFeatureToggles(service, toggles)
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