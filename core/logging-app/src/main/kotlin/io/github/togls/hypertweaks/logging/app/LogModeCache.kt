package io.github.togls.hypertweaks.logging.app

import android.content.Context
import io.github.togls.hypertweaks.logging.api.LogMode

internal class LogModeCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun read(): LogMode {
        return LogMode.fromPersistedValue(
            preferences.getString(KeyLogMode, LogMode.Default.persistedValue),
        )
    }

    fun write(mode: LogMode) {
        check(preferences.edit().putString(KeyLogMode, mode.persistedValue).commit()) {
            "Failed to persist app log mode cache"
        }
    }

    fun shouldRunCleanup(now: Long): Boolean {
        return now - preferences.getLong(KeyLastCleanup, 0L) >= CleanupIntervalMillis
    }

    fun recordCleanup(now: Long) {
        check(preferences.edit().putLong(KeyLastCleanup, now).commit()) {
            "Failed to persist log cleanup time"
        }
    }

    private companion object {
        const val PreferencesName = "logging_runtime"
        const val KeyLogMode = "app_log_mode_cache"
        const val KeyLastCleanup = "last_log_cleanup"
        const val CleanupIntervalMillis = 24L * 60L * 60L * 1_000L
    }
}
