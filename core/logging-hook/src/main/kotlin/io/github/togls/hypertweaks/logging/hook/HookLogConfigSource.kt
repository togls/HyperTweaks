package io.github.togls.hypertweaks.logging.hook

import android.content.SharedPreferences
import io.github.togls.hypertweaks.logging.api.LogMode
import java.util.concurrent.atomic.AtomicReference

class HookLogConfigSource(
    private val preferencesProvider: () -> SharedPreferences,
    private val modeKey: String,
    private val versionKey: String,
    private val recoveryKey: String,
    private val onRecoveryRequested: () -> Unit = {},
    private val onListenerFailure: (Throwable) -> Unit = {},
) : AutoCloseable {
    val current = AtomicReference(HookLogConfig())

    private val lifecycleLock = Any()
    private var preferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun start(): Result<HookLogConfig> = runCatching {
        val remotePreferences = preferencesProvider()
        val changeListener = createListener()
        synchronized(lifecycleLock) {
            preferences = remotePreferences
            listener = changeListener
            try {
                remotePreferences.registerOnSharedPreferenceChangeListener(changeListener)
                remotePreferences.readConfig().also(current::set)
            } catch (error: Throwable) {
                preferences = null
                listener = null
                unregisterAfterFailedStart(remotePreferences, changeListener, error)
                throw error
            }
        }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            val currentPreferences = preferences
            val currentListener = listener
            preferences = null
            listener = null
            if (currentPreferences != null && currentListener != null) {
                runCatching {
                    currentPreferences.unregisterOnSharedPreferenceChangeListener(currentListener)
                }.onFailure(onListenerFailure)
            }
        }
    }

    private fun createListener(): SharedPreferences.OnSharedPreferenceChangeListener {
        return SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            synchronized(lifecycleLock) {
                if (preferences !== sharedPreferences) return@synchronized
                when (key) {
                    recoveryKey -> runCatching(onRecoveryRequested).onFailure(onListenerFailure)
                    modeKey, versionKey -> runCatching {
                        current.set(sharedPreferences.readConfig())
                    }.onFailure(onListenerFailure)
                }
            }
        }
    }

    private fun unregisterAfterFailedStart(
        remotePreferences: SharedPreferences,
        changeListener: SharedPreferences.OnSharedPreferenceChangeListener,
        startError: Throwable,
    ) {
        try {
            remotePreferences.unregisterOnSharedPreferenceChangeListener(changeListener)
        } catch (cleanupError: Throwable) {
            startError.addSuppressed(cleanupError)
        }
    }

    private fun SharedPreferences.readConfig(): HookLogConfig {
        return HookLogConfig(
            mode = LogMode.fromPersistedValue(getString(modeKey, LogMode.Default.persistedValue)),
            version = getLong(versionKey, 0L),
        )
    }
}
