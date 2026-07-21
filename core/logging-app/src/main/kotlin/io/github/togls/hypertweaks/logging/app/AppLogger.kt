package io.github.togls.hypertweaks.logging.app

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.logging.api.LoggerFactory
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogSource

object AppLogger {
    fun create(
        modeProvider: () -> LogMode,
        androidSink: LogSink,
        roomSink: LogSink,
        context: LogContext,
    ): Logger {
        return LoggerFactory.create(
            source = LogSource.APP,
            modeProvider = modeProvider,
            sink = AppDispatchSink(androidSink, roomSink),
            context = context,
        )
    }
}

private class AppDispatchSink(
    private val androidSink: LogSink,
    private val roomSink: LogSink,
) : LogSink {
    override fun emit(event: LogEvent) {
        runCatching { androidSink.emit(event) }
        runCatching { roomSink.emit(event) }
    }
}
