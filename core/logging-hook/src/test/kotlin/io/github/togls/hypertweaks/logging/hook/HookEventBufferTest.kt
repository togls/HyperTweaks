package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogEventBuilder
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookEventBufferTest {
    @Test
    fun `full buffer evicts oldest least important event`() {
        val buffer = HookEventBuffer(capacity = 3)
        buffer.offer(event(LogLevel.DEBUG, "debug"))
        buffer.offer(event(LogLevel.INFO, "info"))
        buffer.offer(event(LogLevel.WARN, "warn"))

        assertTrue(buffer.offer(event(LogLevel.ERROR, "error")))

        assertEquals(
            listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            buffer.peekBatch().map(LogEvent::level),
        )
        assertEquals(1L, buffer.consumeDroppedCount())
    }

    @Test
    fun `less important incoming event cannot evict errors`() {
        val buffer = HookEventBuffer(capacity = 2)
        buffer.offer(event(LogLevel.ERROR, "first"))
        buffer.offer(event(LogLevel.ERROR, "second"))

        assertFalse(buffer.offer(event(LogLevel.DEBUG, "debug")))
        assertEquals(listOf("first", "second"), buffer.peekBatch().map(LogEvent::message))
    }

    @Test
    fun `acknowledge removes only successfully sent prefix`() {
        val buffer = HookEventBuffer(capacity = 4)
        repeat(3) { index -> buffer.offer(event(LogLevel.INFO, index.toString())) }

        buffer.acknowledge(2)

        assertEquals(listOf("2"), buffer.peekBatch().map(LogEvent::message))
    }

    private fun event(level: LogLevel, message: String): LogEvent {
        return LogEventBuilder(LogSource.HOOK).build(
            context = LogContext(),
            level = level,
            event = "hook.callback.completed",
            message = message,
            throwable = null,
            fields = emptyMap(),
        )
    }
}
