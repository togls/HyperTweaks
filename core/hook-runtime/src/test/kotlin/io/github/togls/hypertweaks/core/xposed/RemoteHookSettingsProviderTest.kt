package io.github.togls.hypertweaks.core.xposed

import android.content.SharedPreferences
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.logging.api.NoOpLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteHookSettingsProviderTest {
    @Test
    fun preferenceChangeAtomicallyReplacesCompleteSnapshot() {
        val preferences = FakeSharedPreferences(
            mutableMapOf(
                RemotePreferenceKeys.ImeEnabled to true,
                RemotePreferenceKeys.NavBarLayoutHandle to "initial",
                RemotePreferenceKeys.KeepAliveMode to "oom_only",
            ),
        )
        val provider = RemoteHookSettingsProvider.create({ preferences }, NoOpLogger)

        preferences.update(
            mapOf(
                RemotePreferenceKeys.ImeEnabled to false,
                RemotePreferenceKeys.KeepAliveEnabled to true,
                RemotePreferenceKeys.NavBarLayoutHandle to "updated",
                RemotePreferenceKeys.KeepAliveMode to "conservative",
                RemotePreferenceKeys.KeepAlivePackages to "org.example.app",
            ),
        )

        val snapshot = (provider.currentState as HookSettingsState.Ready).snapshot
        assertFalse(snapshot.isEnabled(RemotePreferenceKeys.ImeEnabled))
        assertTrue(snapshot.isEnabled(RemotePreferenceKeys.KeepAliveEnabled))
        assertEquals("updated", snapshot.navBarLayoutHandle)
        assertEquals("conservative", snapshot.keepAliveMode)
        assertEquals("org.example.app", snapshot.keepAlivePackages)
    }

    @Test
    fun preferenceFailureIsReportedAsUnavailable() {
        val error = IllegalStateException("remote preferences unavailable")
        val provider = RemoteHookSettingsProvider.create(
            preferencesProvider = { throw error },
            logger = NoOpLogger,
        )

        val state = provider.currentState as HookSettingsState.Unavailable
        assertEquals(error, state.reason)
    }
}

private class FakeSharedPreferences(
    private val values: MutableMap<String, Any?>,
) : SharedPreferences {
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    fun update(nextValues: Map<String, Any?>) {
        values.putAll(nextValues)
        listeners.toList().forEach { listener ->
            listener.onSharedPreferenceChanged(this, null)
        }
    }

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defaultValue: String?): String? {
        return values[key] as? String ?: defaultValue
    }

    override fun getStringSet(key: String?, defaultValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defaultValues
    }

    override fun getInt(key: String?, defaultValue: Int): Int {
        return values[key] as? Int ?: defaultValue
    }

    override fun getLong(key: String?, defaultValue: Long): Long {
        return values[key] as? Long ?: defaultValue
    }

    override fun getFloat(key: String?, defaultValue: Float): Float {
        return values[key] as? Float ?: defaultValue
    }

    override fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = error("Not required by read-only Hook settings")

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) listeners -= listener
    }
}
