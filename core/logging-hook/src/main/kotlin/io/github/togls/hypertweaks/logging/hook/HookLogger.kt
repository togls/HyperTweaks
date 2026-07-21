package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.logging.api.LoggerFactory
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogSource

object HookLogger {
    fun create(
        configSource: HookLogConfigSource,
        xposedSink: LogSink,
        fallbackSink: LogSink,
        bridgeSink: LogSink,
        context: LogContext = LogContext(),
    ): Logger {
        val dispatchSink = HookDispatchSink(xposedSink, fallbackSink, bridgeSink)
        return LoggerFactory.create(
            source = LogSource.HOOK,
            modeProvider = { configSource.current.get().mode },
            sink = dispatchSink,
            context = context,
        )
    }
}

internal class HookDispatchSink(
    private val xposedSink: LogSink,
    private val fallbackSink: LogSink,
    private val bridgeSink: LogSink,
) : LogSink {
    override fun emit(event: LogEvent) {
        val xposedResult = runCatching { xposedSink.emit(event) }
        if (xposedResult.isFailure) runCatching { fallbackSink.emit(event) }
        runCatching { bridgeSink.emit(event) }
    }
}
