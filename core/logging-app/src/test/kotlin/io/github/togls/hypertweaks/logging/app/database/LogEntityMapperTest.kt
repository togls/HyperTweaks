package io.github.togls.hypertweaks.logging.app.database

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntityMapperTest {
    @Test
    fun `entity round trip preserves event and escaped fields`() {
        val event = event(
            fields = mapOf(
                "quoted" to "a \"value\"",
                "path" to "C:\\logs\nnext",
            ),
        )

        val restored = LogEntityMapper.toEvent(LogEntityMapper.toEntity(event, createdAt = 20L))

        assertEquals(event, restored)
    }

    @Test
    fun `invalid stored fields fail closed`() {
        assertEquals(emptyMap<String, String>(), FlatJsonObjectCodec.decode("{invalid"))
    }

    private fun event(fields: Map<String, String>): LogEvent {
        return LogEvent(
            eventId = "event-id",
            timestampMillis = 10L,
            elapsedRealtimeMillis = 5L,
            source = LogSource.HOOK,
            level = LogLevel.WARN,
            tag = "GooglePhotos",
            event = "adapter.probe.rejected",
            message = "Fragment class is unavailable",
            packageName = "com.google.android.apps.photos",
            processName = "photos",
            pid = 100,
            tid = 101,
            sessionId = "session",
            fields = fields,
            throwableType = "java.lang.IllegalStateException",
            throwableMessage = "missing",
            stackTrace = "stack",
        )
    }
}
