package io.github.togls.hypertweaks.data

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import io.github.togls.hypertweaks.service.XposedServiceStore

class XposedConfigRepository(
    private val serviceProvider: () -> XposedService? = { XposedServiceStore.service.value },
) : ConfigRepository {

    override val serviceConnected: Boolean
        get() = serviceProvider() != null

    override fun loadConfig(): Result<NavBarLayoutConfig> {
        return withRemotePreferences { prefs ->
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

    override fun saveConfig(config: NavBarLayoutConfig): Result<NavBarLayoutConfig> {
        return withRemotePreferences { prefs ->
            prefs.edit {
                putString(RemotePreferenceKeys.NavBarLayoutStart, config.start.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.end.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutHandle, config.toHandleLayout())
            }

            config
        }
    }

    override fun loadFeatureToggles(): Result<FeatureToggles> {
        return withRemotePreferences { prefs ->
            FeatureToggles(
                imeEnabled = prefs.getBoolean(RemotePreferenceKeys.ImeEnabled, false),
                keepAliveEnabled = prefs.getBoolean(RemotePreferenceKeys.KeepAliveEnabled, false),
            )
        }
    }

    override fun saveFeatureToggles(toggles: FeatureToggles): Result<FeatureToggles> {
        return withRemotePreferences { prefs ->
            prefs.edit {
                putBoolean(RemotePreferenceKeys.ImeEnabled, toggles.imeEnabled)
                putBoolean(RemotePreferenceKeys.KeepAliveEnabled, toggles.keepAliveEnabled)
            }

            toggles
        }
    }

    override fun loadKeepAlivePackages(): Result<Set<String>> {
        return withRemotePreferences { prefs ->
            val raw = prefs
                .getString(RemotePreferenceKeys.KeepAlivePackages, "")
                .orEmpty()

            KeepAlivePackages.parse(raw)
        }
    }

    override fun saveKeepAlivePackages(packages: Set<String>): Result<Set<String>> {
        return withRemotePreferences { prefs ->
            val normalized = packages.toSortedSet()

            prefs.edit {
                putString(
                    RemotePreferenceKeys.KeepAlivePackages,
                    KeepAlivePackages.format(normalized),
                )
            }

            normalized
        }
    }

    private fun <T> withRemotePreferences(
        block: (SharedPreferences) -> T,
    ): Result<T> {
        return runCatching {
            val service = requireService()

            checkRemotePreferencesSupport(service)

            val prefs = service.getRemotePreferences(RemotePreferenceKeys.GroupName)

            block(prefs)
        }
    }

    private fun requireService(): XposedService {
        return serviceProvider()
            ?: error("Xposed service is not connected")
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