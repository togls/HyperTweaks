package io.github.togls.hypertweaks.logging.app.sink

import android.util.Log
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogTextFormatter

class AndroidLogSink : LogSink {
    override fun emit(event: LogEvent) {
        Log.println(event.level.toPriority(), Tag, LogTextFormatter.format(event))
    }

    private fun LogLevel.toPriority(): Int {
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
