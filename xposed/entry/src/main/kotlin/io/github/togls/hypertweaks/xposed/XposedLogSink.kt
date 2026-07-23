package io.github.togls.hypertweaks.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogTextFormatter

internal class XposedLogSink(
    private val module: XposedModule,
) : LogSink {
    override fun emit(event: LogEvent) {
        module.log(event.level.toAndroidPriority(), Tag, LogTextFormatter.format(event))
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
