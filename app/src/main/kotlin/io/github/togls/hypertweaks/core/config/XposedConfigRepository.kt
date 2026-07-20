package io.github.togls.hypertweaks.core.config

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import io.github.togls.hypertweaks.service.XposedServiceStore

class XposedConfigRepository(
    private val serviceProvider: () -> XposedService? = { XposedServiceStore.service.value },
) : ConfigRepository {

    override val serviceConnected: Boolean
        get() = serviceProvider() != null

    override fun loadConfig(): Result<HyperTweaksConfig> {
        return withRemotePreferences { prefs ->
            val navBarLayout = readNavBarLayout(prefs)

            initializeIfMissing(
                prefs = prefs,
                config = navBarLayout,
            )

            HyperTweaksConfig(
                features = readFeatureToggles(prefs),
                ime = ImeConfig(
                    navBarLayout = navBarLayout,
                ),
                keepAlive = KeepAliveConfig(
                    mode = readKeepAliveMode(prefs),
                    packages = readKeepAlivePackages(prefs),
                ),
            )
        }
    }

    override fun saveImeConfig(config: ImeConfig): Result<ImeConfig> {
        return withRemotePreferences { prefs ->
            prefs.edit {
                putString(RemotePreferenceKeys.NavBarLayoutStart, config.navBarLayout.start.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.navBarLayout.end.value)
                    .putString(
                        RemotePreferenceKeys.NavBarLayoutHandle,
                        config.navBarLayout.toHandleLayout()
                    )
            }

            config
        }
    }

    override fun saveNavBarLayout(config: NavBarLayoutConfig): Result<NavBarLayoutConfig> {
        return withRemotePreferences { prefs ->
            prefs.edit {
                putString(RemotePreferenceKeys.NavBarLayoutStart, config.start.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutEnd, config.end.value)
                    .putString(RemotePreferenceKeys.NavBarLayoutHandle, config.toHandleLayout())
            }

            config
        }
    }

    override fun saveFeatureToggles(toggles: FeatureToggles): Result<FeatureToggles> {
        return withRemotePreferences { prefs ->
            prefs.edit {
                putBoolean(RemotePreferenceKeys.ImeEnabled, toggles.imeEnabled)
                    .putBoolean(
                        RemotePreferenceKeys.GooglePhotosLocationEnabled,
                        toggles.googlePhotosLocationEnabled,
                    )
                    .putBoolean(RemotePreferenceKeys.KeepAliveEnabled, toggles.keepAliveEnabled)
            }

            toggles
        }
    }

    override fun saveKeepAliveConfig(config: KeepAliveConfig): Result<KeepAliveConfig> {
        return withRemotePreferences { prefs ->
            val normalized = config.packages.toSortedSet()

            prefs.edit {
                putString(RemotePreferenceKeys.KeepAliveMode, config.mode.value)
                    .putString(
                        RemotePreferenceKeys.KeepAlivePackages,
                        KeepAlivePackages.format(normalized),
                    )
            }

            config
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

    override fun saveKeepAliveMode(mode: KeepAliveMode): Result<KeepAliveMode> {
        return withRemotePreferences { prefs ->
            prefs.edit {
                putString(RemotePreferenceKeys.KeepAliveMode, mode.value)
            }

            mode
        }
    }

    private fun readNavBarLayout(
        prefs: SharedPreferences,
    ): NavBarLayoutConfig {
        return NavBarLayoutConfig(
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
    }

    private fun readFeatureToggles(
        prefs: SharedPreferences,
    ): FeatureToggles {
        return FeatureToggles(
            imeEnabled = prefs.getBoolean(RemotePreferenceKeys.ImeEnabled, false),
            googlePhotosLocationEnabled = prefs.getBoolean(
                RemotePreferenceKeys.GooglePhotosLocationEnabled,
                false,
            ),
            keepAliveEnabled = prefs.getBoolean(RemotePreferenceKeys.KeepAliveEnabled, false),
        )
    }

    private fun readKeepAlivePackages(
        prefs: SharedPreferences,
    ): Set<String> {
        val raw = prefs
            .getString(RemotePreferenceKeys.KeepAlivePackages, "")
            .orEmpty()

        return KeepAlivePackages.parse(raw)
    }

    private fun readKeepAliveMode(
        prefs: SharedPreferences,
    ): KeepAliveMode {
        return KeepAliveMode.fromValue(
            prefs.getString(
                RemotePreferenceKeys.KeepAliveMode,
                KeepAliveMode.Default.value,
            ),
        )
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
