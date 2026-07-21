package io.github.togls.hypertweaks.logging.api

enum class LogMode(val persistedValue: String) {
    OFF("off"),
    BASIC("basic"),
    DEBUG("debug");

    companion object {
        val Default: LogMode = BASIC

        fun fromPersistedValue(value: String?): LogMode {
            return entries.firstOrNull { mode -> mode.persistedValue == value } ?: Default
        }
    }
}
