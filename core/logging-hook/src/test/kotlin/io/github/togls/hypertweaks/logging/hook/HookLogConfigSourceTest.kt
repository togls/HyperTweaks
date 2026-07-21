package io.github.togls.hypertweaks.logging.hook

import android.content.SharedPreferences
import io.github.togls.hypertweaks.logging.api.LogMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HookLogConfigSourceTest {
    @Test
    fun `update during listener registration is not overwritten`() {
        val preferences = FakeSharedPreferences().apply {
            putString(ModeKey, LogMode.BASIC.persistedValue, notify = false)
            onBeforeListenerRegistered = {
                putString(ModeKey, LogMode.DEBUG.persistedValue, notify = false)
                putLong(VersionKey, 7L, notify = false)
            }
        }
        val source = createSource(preferences)

        val result = source.start().getOrThrow()

        assertEquals(LogMode.DEBUG, result.mode)
        assertEquals(7L, result.version)
        assertEquals(result, source.current.get())
        source.close()
    }

    @Test
    fun `recovery key notifies only while source is active`() {
        val preferences = FakeSharedPreferences()
        var recoveryCount = 0
        val source = createSource(preferences) { recoveryCount++ }
        source.start().getOrThrow()

        preferences.putLong(RecoveryKey, 1L)
        preferences.putLong("unrelated", 1L)
        source.close()
        preferences.putLong(RecoveryKey, 2L)

        assertEquals(1, recoveryCount)
    }

    private fun createSource(
        preferences: FakeSharedPreferences,
        onRecoveryRequested: () -> Unit = {},
    ): HookLogConfigSource {
        return HookLogConfigSource(
            preferencesProvider = { preferences },
            modeKey = ModeKey,
            versionKey = VersionKey,
            recoveryKey = RecoveryKey,
            onRecoveryRequested = onRecoveryRequested,
        )
    }

    private companion object {
        const val ModeKey = "mode"
        const val VersionKey = "version"
        const val RecoveryKey = "recovery"
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    var onBeforeListenerRegistered: (() -> Unit)? = null

    fun putString(key: String, value: String, notify: Boolean = true) {
        values[key] = value
        if (notify) notifyChanged(key)
    }

    fun putLong(key: String, value: Long, notify: Boolean = true) {
        values[key] = value
        if (notify) notifyChanged(key)
    }

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defaultValue: String?): String? {
        return values[key] as? String ?: defaultValue
    }

    override fun getStringSet(key: String, defaultValues: Set<String>?): Set<String>? = defaultValues
    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
    override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun contains(key: String): Boolean = key in values
    override fun edit(): SharedPreferences.Editor = error("Editing is not needed by this test")

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        onBeforeListenerRegistered?.invoke()
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private fun notifyChanged(key: String) {
        listeners.toList().forEach { listener -> listener.onSharedPreferenceChanged(this, key) }
    }
}
