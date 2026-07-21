package io.github.togls.hypertweaks.logging.app.sink

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogLimits
import io.github.togls.hypertweaks.logging.api.LogSink

class AppBufferSink(
    private val capacity: Int = LogLimits.AppBufferEventCount,
) : LogSink {
    private val events = ArrayDeque<LogEvent>()
    private var droppedCount = 0L

    override fun emit(event: LogEvent) {
        offer(event)
    }

    @Synchronized
    fun offer(event: LogEvent): Boolean {
        if (events.size < capacity) {
            events.addLast(event)
            return true
        }
        val lowestLevel = events.minOf(LogEvent::level)
        if (event.level < lowestLevel) {
            droppedCount++
            return false
        }
        val index = events.indexOfFirst { existing -> existing.level == lowestLevel }
        if (index >= 0) events.removeAt(index)
        events.addLast(event)
        droppedCount++
        return true
    }

    @Synchronized
    fun drain(): List<LogEvent> {
        val drained = events.toList()
        events.clear()
        return drained
    }

    @Synchronized
    fun droppedCount(): Long = droppedCount
}
