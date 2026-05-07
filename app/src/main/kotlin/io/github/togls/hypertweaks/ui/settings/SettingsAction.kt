package io.github.togls.hypertweaks.ui.settings

import io.github.togls.hypertweaks.data.NavBarButton

sealed interface SettingsAction {
    data class SetImeEnabled(val enabled: Boolean) : SettingsAction

    data class SetKeepAliveEnabled(val enabled: Boolean) : SettingsAction

    data class SetStartButton(val button: NavBarButton) : SettingsAction

    data class SetEndButton(val button: NavBarButton) : SettingsAction

    data class UpdateKeepAlivePackagesText(val text: String) : SettingsAction

    data object SaveKeepAlivePackages : SettingsAction

    data object ReloadConfig : SettingsAction
}