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
        runCatching {
            val remotePreferences = preferencesProvider()
            val listener = createPreferenceListener()
            remotePreferences.registerOnSharedPreferenceChangeListener(listener)
            preferences = remotePreferences
            preferenceListener = listener
            publish(readState(remotePreferences))
        }.onFailure { error ->
            preferences = null
            preferenceListener = null
            publish(HookSettingsState.Unavailable(error))
        }
    }

    private fun unregisterPreferenceListener() {
        val currentPreferences = preferences
        val currentListener = preferenceListener
        preferences = null
        preferenceListener = null
        if (currentPreferences != null && currentListener != null) {
            runCatching {
                currentPreferences.unregisterOnSharedPreferenceChangeListener(currentListener)
            }.onFailure(::logListenerFailure)
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
        return runCatching {
            HookSettingsState.Ready(remotePreferences.readSnapshot())
        }.getOrElse { error ->
            HookSettingsState.Unavailable(error)
        }
    }

    private fun SharedPreferences.readSnapshot(): HookSettingsSnapshot {
        return HookSettingsSnapshot(
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
            runCatching { subscriber(nextState) }.onFailure(::logListenerFailure)
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
