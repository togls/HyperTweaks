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
import io.github.togls.hypertweaks.core.config.XposedConfigRepository
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages

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

            is SettingsAction.SetGooglePhotosLocationEnabled -> {
                updateGooglePhotosLocationEnabled(
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
        configRepository
            .loadConfig()
            .onSuccess { config ->
                uiState = config.toSettingsUiState(
                    serviceConnected = true,
                    message = string(R.string.status_config_loaded),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = configRepository.serviceConnected,
                        message = error.message ?: string(R.string.status_read_config_failed),
                    ),
                )
            }
    }

    private fun updateStartButton(
        button: NavBarButton,
    ) {
        val nextLayout = uiState.ime.navBarLayout.copy(
            start = button,
        )

        saveNavBarLayout(nextLayout)
    }

    private fun updateEndButton(
        button: NavBarButton,
    ) {
        val nextLayout = uiState.ime.navBarLayout.copy(end = button)

        saveNavBarLayout(nextLayout)
    }

    private fun saveNavBarLayout(
        layout: NavBarLayoutConfig,
    ) {
        configRepository
            .saveNavBarLayout(layout)
            .onSuccess { savedLayout ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = true,
                        message = string(R.string.status_config_saved),
                    ),
                    ime = uiState.ime.copy(
                        navBarLayout = savedLayout,
                    ),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = configRepository.serviceConnected,
                        message = error.message ?: string(R.string.status_save_config_failed),
                    ),
                )
            }
    }

    private fun updateKeepAlivePackagesText(text: String) {
        val parseResult = KeepAlivePackages.parseWithInvalid(text)

        uiState = uiState.copy(
            keepAlive = uiState.keepAlive.copy(
                packagesText = text,
                invalidPackages = parseResult.invalidValues,
            ),
        )
    }

    private fun saveKeepAlivePackages() {
        val parseResult = KeepAlivePackages.parseWithInvalid(uiState.keepAlive.packagesText)

        if (parseResult.invalidValues.isNotEmpty()) {
            uiState = uiState.copy(
                service = uiState.service.copy(
                    connected = true,
                    message = string(
                        R.string.status_keep_alive_invalid_packages,
                        parseResult.invalidValues.joinToString(),
                    ),
                ),
                keepAlive = uiState.keepAlive.copy(
                    invalidPackages = parseResult.invalidValues,
                ),
            )
            return
        }

        configRepository
            .saveKeepAlivePackages(parseResult.packages)
            .onSuccess { savedPackages ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = true,
                        message = string(R.string.status_keep_alive_saved),
                    ),
                    keepAlive = uiState.keepAlive.copy(
                        packagesText = KeepAlivePackages.format(savedPackages),
                        invalidPackages = emptyList()
                    )

                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = true,
                        message = error.message ?: string(R.string.status_save_config_failed),
                    )
                )
            }
    }

    private fun updateImeEnabled(
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            toggles = currentFeatureToggles().copy(imeEnabled = enabled),
        )
    }

    private fun updateGooglePhotosLocationEnabled(
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            toggles = currentFeatureToggles().copy(googlePhotosLocationEnabled = enabled),
        )
    }

    private fun updateKeepAliveEnabled(
        enabled: Boolean,
    ) {
        saveFeatureToggles(
            toggles = currentFeatureToggles().copy(keepAliveEnabled = enabled),
        )
    }

    private fun saveFeatureToggles(
        toggles: FeatureToggles,
    ) {
        configRepository
            .saveFeatureToggles(toggles)
            .onSuccess { savedToggles ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = true,
                        message = string(R.string.status_feature_toggle_saved),
                    ),
                    ime = uiState.ime.copy(
                        enabled = savedToggles.imeEnabled,
                    ),
                    googlePhotos = uiState.googlePhotos.copy(
                        enabled = savedToggles.googlePhotosLocationEnabled,
                    ),
                    keepAlive = uiState.keepAlive.copy(
                        enabled = savedToggles.keepAliveEnabled,
                    ),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = configRepository.serviceConnected,
                        message = error.message ?: string(R.string.status_save_config_failed),
                    )
                )
            }
    }

    private fun currentFeatureToggles(): FeatureToggles {
        return FeatureToggles(
            imeEnabled = uiState.ime.enabled,
            googlePhotosLocationEnabled = uiState.googlePhotos.enabled,
            keepAliveEnabled = uiState.keepAlive.enabled,
        )
    }

    private fun updateKeepAliveMode(mode: KeepAliveMode) {
        if (!configRepository.serviceConnected) {
            uiState = uiState.copy(
                service = uiState.service.copy(
                    connected = false,
                    message = string(R.string.status_save_without_service),
                )
            )
            return
        }

        configRepository
            .saveKeepAliveMode(mode)
            .onSuccess { savedMode ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = true,
                        message = string(R.string.status_keep_alive_mode_saved),
                    ),
                    keepAlive = uiState.keepAlive.copy(
                        mode = savedMode,
                    ),
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    service = uiState.service.copy(
                        connected = configRepository.serviceConnected,
                        message = error.message ?: string(R.string.status_save_config_failed),
                    )
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
