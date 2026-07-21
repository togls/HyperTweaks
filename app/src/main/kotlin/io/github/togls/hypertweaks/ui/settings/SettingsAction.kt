package io.github.togls.hypertweaks.ui.settings

import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.logging.api.LogMode

sealed interface SettingsAction {

    data class SetLogMode(
        val mode: LogMode,
    ) : SettingsAction

    data class SetImeEnabled(
        val enabled: Boolean,
    ) : SettingsAction

    data class SetGooglePhotosLocationEnabled(
        val enabled: Boolean,
    ) : SettingsAction

    data class SetKeepAliveEnabled(
        val enabled: Boolean,
    ) : SettingsAction

    data class SetStartButton(
        val button: NavBarButton,
    ) : SettingsAction

    data class SetEndButton(
        val button: NavBarButton,
    ) : SettingsAction

    data class UpdateKeepAlivePackagesText(
        val text: String,
    ) : SettingsAction

    data object SaveKeepAlivePackages : SettingsAction

    data object ReloadConfig : SettingsAction

    data class SetKeepAliveMode(
        val mode: KeepAliveMode,
    ) : SettingsAction
}
