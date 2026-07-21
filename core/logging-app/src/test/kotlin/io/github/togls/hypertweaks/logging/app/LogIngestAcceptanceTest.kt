package io.github.togls.hypertweaks.logging.app

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEventBuilder
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LogIngestAcceptanceTest {
    @Test
    fun `stops at first rejected event so acknowledgement remains a prefix`() {
        val events = (1..3).map(::event)
        val offeredEventIds = mutableListOf<String>()

        val acceptedCount = countAcceptedPrefix(events) { candidate ->
            offeredEventIds += candidate.eventId
            candidate !== events[1]
        }

        assertEquals(1, acceptedCount)
        assertEquals(events.take(2).map { it.eventId }, offeredEventIds)
    }

    private fun event(index: Int) = LogEventBuilder(LogSource.HOOK).build(
        context = LogContext(),
        level = LogLevel.INFO,
        event = "provider.batch.received",
        message = index.toString(),
        throwable = null,
        fields = emptyMap(),
    )
}
