package io.github.togls.hypertweaks.logging.hook.sink

import android.util.Log
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogTextFormatter

class AndroidFallbackSink : LogSink {
    override fun emit(event: LogEvent) {
        Log.println(event.level.toAndroidPriority(), Tag, LogTextFormatter.format(event))
    }

    private fun LogLevel.toAndroidPriority(): Int {
        return when (this) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
    }

    private companion object {
        const val Tag = "HyperTweaks"
    }
}
