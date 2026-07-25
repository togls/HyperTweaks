package io.github.togls.hypertweaks.core.xposed

import android.content.SharedPreferences
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.logging.api.Logger
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference

class RemoteHookSettingsProvider private constructor(
    private val preferencesProvider: () -> SharedPreferences,
    private val logger: Logger,
) : HookSettingsProvider, AutoCloseable {
    private val state = AtomicReference<HookSettingsState>(
        HookSettingsState.Unavailable(null),
    )
    private val subscribers = CopyOnWriteArraySet<(HookSettingsState) -> Unit>()
    private val lifecycleLock = Any()
    private var preferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override val currentState: HookSettingsState
        get() = state.get()

    init {
        refresh()
    }

    fun refresh() {
        synchronized(lifecycleLock) {
            val currentPreferences = preferences
            if (currentPreferences != null) {
                publish(readState(currentPreferences))
                return
            }
            start()
        }
    }

    override fun refreshIfUnavailable() {
        val unavailableState = currentState as? HookSettingsState.Unavailable ?: return
        if (!unavailableState.retryable) return
        refresh()
        val refreshedState = currentState as? HookSettingsState.Ready ?: return
        logger.info(
            event = "config.snapshot.recovered",
            fields = mapOf("config_version" to refreshedState.snapshot.version.toString()),
        )
    }

    override fun subscribe(listener: (HookSettingsState) -> Unit): HookSettingsSubscription {
        subscribers += listener
        return HookSettingsSubscription { subscribers -= listener }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            unregisterPreferenceListener()
        }
        subscribers.clear()
    }

    private fun start() {
        try {
            val remotePreferences = preferencesProvider()
            val listener = createPreferenceListener()
            remotePreferences.registerOnSharedPreferenceChangeListener(listener)
            preferences = remotePreferences
            preferenceListener = listener
            publish(readState(remotePreferences))
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            preferences = null
            preferenceListener = null
            publish(unavailableState(error))
        }
    }

    private fun unregisterPreferenceListener() {
        val currentPreferences = preferences
        val currentListener = preferenceListener
        preferences = null
        preferenceListener = null
        if (currentPreferences != null && currentListener != null) {
            try {
                currentPreferences.unregisterOnSharedPreferenceChangeListener(currentListener)
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                logListenerFailure(error)
            }
        }
    }

    private fun createPreferenceListener(): SharedPreferences.OnSharedPreferenceChangeListener {
        return SharedPreferences.OnSharedPreferenceChangeListener { changedPreferences, _ ->
            synchronized(lifecycleLock) {
                if (preferences !== changedPreferences) return@synchronized
                publish(readState(changedPreferences))
            }
        }
    }

    private fun readState(remotePreferences: SharedPreferences): HookSettingsState {
        return try {
            HookSettingsState.Ready(remotePreferences.readSnapshot())
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            unavailableState(error)
        }
    }

    private fun unavailableState(error: Throwable): HookSettingsState.Unavailable {
        return HookSettingsState.Unavailable(
            reason = error,
            retryable = error !is UnsupportedOperationException,
        )
    }

    private fun SharedPreferences.readSnapshot(): HookSettingsSnapshot {
        return HookSettingsSnapshot(
            version = getLong(RemotePreferenceKeys.HookConfigVersion, 0L),
            systemServerFeaturesEnabled = getBoolean(
                RemotePreferenceKeys.SystemServerFeaturesEnabled,
                true,
            ),
            enabledPreferenceKeys = readEnabledPreferenceKeys(),
            navBarLayoutStart = getString(
                RemotePreferenceKeys.NavBarLayoutStart,
                HookSettingsSnapshot.DefaultNavBarStart,
            ) ?: HookSettingsSnapshot.DefaultNavBarStart,
            navBarLayoutEnd = getString(
                RemotePreferenceKeys.NavBarLayoutEnd,
                HookSettingsSnapshot.DefaultNavBarEnd,
            ) ?: HookSettingsSnapshot.DefaultNavBarEnd,
            navBarLayoutHandle = getString(RemotePreferenceKeys.NavBarLayoutHandle, "").orEmpty(),
            keepAliveMode = getString(
                RemotePreferenceKeys.KeepAliveMode,
                HookSettingsSnapshot.DefaultKeepAliveMode,
            ) ?: HookSettingsSnapshot.DefaultKeepAliveMode,
            keepAlivePackages = getString(RemotePreferenceKeys.KeepAlivePackages, "").orEmpty(),
        )
    }

    private fun SharedPreferences.readEnabledPreferenceKeys(): Set<String> {
        return setOf(
            RemotePreferenceKeys.ImeEnabled,
            RemotePreferenceKeys.GooglePhotosLocationEnabled,
            RemotePreferenceKeys.KeepAliveEnabled,
        ).filterTo(mutableSetOf()) { preferenceKey ->
            getBoolean(preferenceKey, false)
        }
    }

    private fun publish(nextState: HookSettingsState) {
        state.set(nextState)
        subscribers.forEach { subscriber ->
            try {
                subscriber(nextState)
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                logListenerFailure(error)
            }
        }
    }

    private fun logListenerFailure(error: Throwable) {
        logger.error(
            event = "config.snapshot.listener.failed",
            throwable = error,
        )
    }

    companion object {
        fun create(
            preferencesProvider: () -> SharedPreferences,
            logger: Logger,
        ): RemoteHookSettingsProvider {
            return RemoteHookSettingsProvider(preferencesProvider, logger)
        }
    }
}
