package io.github.togls.hypertweaks.logging.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerTest {
    @Test
    fun `child context is immutable and fields override parent`() {
        val events = mutableListOf<LogEvent>()
        val root = logger(events).withField("feature", "root")
        val child = root.child("GooglePhotos").withField("feature", "map")

        child.info("hook.install.succeeded", fields = mapOf("target" to "photos"))
        root.info("module.load.succeeded")

        assertEquals("GooglePhotos", events[0].tag)
        assertEquals("map", events[0].fields["feature"])
        assertEquals("photos", events[0].fields["target"])
        assertEquals("", events[1].tag)
        assertEquals("root", events[1].fields["feature"])
    }

    @Test
    fun `off avoids event construction and debug is filtered in basic`() {
        val events = mutableListOf<LogEvent>()
        var mode = LogMode.OFF
        val logger = logger(events) { mode }

        logger.error("hook.callback.failed")
        mode = LogMode.BASIC
        logger.debug("hook.callback.started")
        logger.info("hook.callback.completed")

        assertEquals(listOf(LogLevel.INFO), events.map(LogEvent::level))
    }

    @Test
    fun `throwable and oversized content are preserved within limits`() {
        val events = mutableListOf<LogEvent>()
        val error = IllegalStateException("broken")

        logger(events).error(
            event = "database.write.failed",
            message = "x".repeat(LogLimits.MaxMessageChars + 10),
            throwable = error,
        )

        val event = events.single()
        assertEquals(LogLimits.MaxMessageChars, event.message?.length)
        assertEquals(IllegalStateException::class.java.name, event.throwableType)
        assertTrue(event.stackTrace.orEmpty().contains("IllegalStateException"))
    }

    @Test
    fun `event ids are unique and names follow stable convention`() {
        val events = mutableListOf<LogEvent>()
        val logger = logger(events)

        logger.info("hook.install.started")
        logger.info("hook.install.started")

        assertNotEquals(events[0].eventId, events[1].eventId)
        assertTrue(LogEvent.isValidEventName(events[0].event))
        assertFalse(LogEvent.isValidEventName("install_start"))
    }

    private fun logger(
        events: MutableList<LogEvent>,
        modeProvider: () -> LogMode = { LogMode.DEBUG },
    ): Logger {
        return LoggerFactory.create(
            source = LogSource.HOOK,
            modeProvider = modeProvider,
            sink = LogSink(events::add),
        )
    }
}
