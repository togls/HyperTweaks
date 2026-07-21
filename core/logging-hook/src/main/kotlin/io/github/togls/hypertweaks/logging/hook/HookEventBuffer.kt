package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogLimits

class HookEventBuffer(
    private val capacity: Int = LogLimits.HookBufferEventCount,
) {
    private val events = ArrayDeque<LogEvent>()
    private var droppedCount = 0L

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
        removeOldest(lowestLevel)
        events.addLast(event)
        droppedCount++
        return true
    }

    @Synchronized
    fun peekBatch(
        maxEvents: Int = LogLimits.IpcBatchEventCount,
        maxBytes: Int = LogLimits.IpcTargetBatchBytes,
    ): List<LogEvent> {
        val batch = mutableListOf<LogEvent>()
        var estimatedBytes = 0
        for (event in events) {
            val eventBytes = estimateBytes(event)
            if (batch.isNotEmpty() && estimatedBytes + eventBytes > maxBytes) break
            batch += event
            estimatedBytes += eventBytes
            if (batch.size >= maxEvents) break
        }
        return batch
    }

    @Synchronized
    fun acknowledge(count: Int) {
        repeat(count.coerceAtMost(events.size)) { events.removeFirst() }
    }

    @Synchronized
    fun consumeDroppedCount(): Long {
        val count = droppedCount
        droppedCount = 0L
        return count
    }

    @Synchronized
    fun size(): Int = events.size

    private fun removeOldest(level: LogLevel) {
        val index = events.indexOfFirst { event -> event.level == level }
        if (index >= 0) events.removeAt(index)
    }

    private fun estimateBytes(event: LogEvent): Int {
        val textBytes = listOfNotNull(
            event.eventId,
            event.event,
            event.message,
            event.stackTrace,
        ).sumOf(String::length) * 2
        val fieldBytes = event.fields.entries.sumOf { (key, value) -> (key.length + value.length) * 2 }
        return textBytes + fieldBytes + 128
    }
}
