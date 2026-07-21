package io.github.togls.hypertweaks.logging.hook

import android.content.SharedPreferences
import io.github.togls.hypertweaks.logging.api.LogMode
import java.util.concurrent.atomic.AtomicReference

class HookLogConfigSource(
    private val preferencesProvider: () -> SharedPreferences,
    private val modeKey: String,
    private val versionKey: String,
) : AutoCloseable {
    val current = AtomicReference(HookLogConfig())

    private var preferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun start(): Result<HookLogConfig> = runCatching {
        val remotePreferences = preferencesProvider()
        val initial = remotePreferences.readConfig()
        val changeListener = createListener()
        remotePreferences.registerOnSharedPreferenceChangeListener(changeListener)
        preferences = remotePreferences
        listener = changeListener
        current.set(initial)
        initial
    }

    override fun close() {
        val currentPreferences = preferences
        val currentListener = listener
        if (currentPreferences != null && currentListener != null) {
            runCatching { currentPreferences.unregisterOnSharedPreferenceChangeListener(currentListener) }
        }
        preferences = null
        listener = null
    }

    private fun createListener(): SharedPreferences.OnSharedPreferenceChangeListener {
        return SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == modeKey || key == versionKey) {
                runCatching { current.set(sharedPreferences.readConfig()) }
            }
        }
    }

    private fun SharedPreferences.readConfig(): HookLogConfig {
        return HookLogConfig(
            mode = LogMode.fromPersistedValue(getString(modeKey, LogMode.Default.persistedValue)),
            version = getLong(versionKey, 0L),
        )
    }
}
