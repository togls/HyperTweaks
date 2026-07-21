package io.github.togls.hypertweaks.logging.app.sink

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogEventBuilder
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppBufferSinkTest {
    @Test
    fun `buffer retains more important events`() {
        val buffer = AppBufferSink(capacity = 2)
        buffer.offer(event(LogLevel.ERROR, "first"))
        buffer.offer(event(LogLevel.WARN, "second"))

        assertFalse(buffer.offer(event(LogLevel.DEBUG, "discarded")))

        assertEquals(listOf("first", "second"), buffer.drain().map(LogEvent::message))
        assertEquals(1L, buffer.droppedCount())
    }

    private fun event(level: LogLevel, message: String): LogEvent {
        return LogEventBuilder(LogSource.APP).build(
            context = LogContext(),
            level = level,
            event = "database.write.failed",
            message = message,
            throwable = null,
            fields = emptyMap(),
        )
    }
}
