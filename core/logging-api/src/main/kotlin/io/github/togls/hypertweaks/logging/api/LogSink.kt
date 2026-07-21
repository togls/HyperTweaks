package io.github.togls.hypertweaks.logging.api

fun interface LogSink {
    fun emit(event: LogEvent)
}

class CompositeLogSink(
    private val sinks: List<LogSink>,
) : LogSink {
    override fun emit(event: LogEvent) {
        sinks.forEach { sink ->
            runCatching { sink.emit(event) }
        }
    }
}
