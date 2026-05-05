package io.github.togls.hypertweaks.data

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import androidx.core.content.edit

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

            prefs.edit {
                putString(RemotePreferenceKeys.NavBarLayoutStart, config.start.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.end.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutHandle, config.toHandleLayout())
            }

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

        prefs.edit {
            putString(RemotePreferenceKeys.NavBarLayoutStart, config.start.value)
                .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.end.value)
                .putString(RemotePreferenceKeys.NavBarLayoutHandle, config.toHandleLayout())
        }
    }

    private fun checkRemotePreferencesSupport(service: XposedService) {
        val supported = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L

        if (!supported) {
            error("The current Xposed framework does not support remote preferences")
        }
    }
}