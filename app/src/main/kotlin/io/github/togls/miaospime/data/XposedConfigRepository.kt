package io.github.togls.miaospime.data

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

class XposedConfigRepository {

    fun loadConfig(service: XposedService): Result<NavBarLayoutConfig> {
        return runCatching {
            checkRemotePreferencesSupport(service)

            val prefs = service.getRemotePreferences(RemotePreferenceKeys.GroupName)

            val config = NavBarLayoutConfig(
                start = NavBarButton.fromValue(
                    prefs.getString(
                        RemotePreferenceKeys.NavBarLayoutStart,
                        NavBarButton.Back.value,
                    ),
                ),
                end = NavBarButton.fromValue(
                    prefs.getString(
                        RemotePreferenceKeys.NavBarLayoutEnd,
                        NavBarButton.ImeSwitcher.value,
                    ),
                ),
            )

            initializeIfMissing(prefs, config)

            config
        }
    }

    fun saveConfig(
        service: XposedService,
        config: NavBarLayoutConfig,
    ): Result<NavBarLayoutConfig> {
        return runCatching {
            checkRemotePreferencesSupport(service)

            val prefs = service.getRemotePreferences(RemotePreferenceKeys.GroupName)

            prefs.edit()
                .putString(RemotePreferenceKeys.NavBarLayoutStart, config.start.value)
                .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.end.value)
                .putString(RemotePreferenceKeys.NavBarLayoutHandle, config.toHandleLayout())
                .apply()

            config
        }
    }

    private fun initializeIfMissing(
        prefs: SharedPreferences,
        config: NavBarLayoutConfig,
    ) {
        val currentHandle = prefs.getString(RemotePreferenceKeys.NavBarLayoutHandle, null)

        if (!currentHandle.isNullOrBlank()) {
            return
        }

        prefs.edit()
            .putString(RemotePreferenceKeys.NavBarLayoutStart, config.start.value)
            .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.end.value)
            .putString(RemotePreferenceKeys.NavBarLayoutHandle, config.toHandleLayout())
            .apply()
    }

    private fun checkRemotePreferencesSupport(service: XposedService) {
        val supported = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L

        if (!supported) {
            error("当前 Xposed framework 不支持 remote preferences")
        }
    }
}