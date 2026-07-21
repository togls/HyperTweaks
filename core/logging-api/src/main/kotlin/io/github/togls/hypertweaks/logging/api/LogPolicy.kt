package io.github.togls.hypertweaks.logging.api

object LogPolicy {
    fun allows(mode: LogMode, level: LogLevel): Boolean {
        return when (mode) {
            LogMode.OFF -> false
            LogMode.BASIC -> level != LogLevel.DEBUG
            LogMode.DEBUG -> true
        }
    }
}
