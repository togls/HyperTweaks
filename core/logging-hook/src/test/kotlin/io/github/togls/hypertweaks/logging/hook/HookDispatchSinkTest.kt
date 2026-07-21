package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogEventBuilder
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HookDispatchSinkTest {
    @Test
    fun `successful xposed output skips fallback and still bridges`() {
        val calls = mutableListOf<String>()
        val sink = HookDispatchSink(
            xposedSink = namedSink("xposed", calls),
            fallbackSink = namedSink("fallback", calls),
            bridgeSink = namedSink("bridge", calls),
        )

        sink.emit(event())

        assertEquals(listOf("xposed", "bridge"), calls)
    }

    @Test
    fun `xposed failure uses fallback without blocking bridge`() {
        val calls = mutableListOf<String>()
        val sink = HookDispatchSink(
            xposedSink = LogSink {
                calls += "xposed"
                error("unavailable")
            },
            fallbackSink = namedSink("fallback", calls),
            bridgeSink = namedSink("bridge", calls),
        )

        sink.emit(event())

        assertEquals(listOf("xposed", "fallback", "bridge"), calls)
    }

    @Test
    fun `bridge failure does not escape logger`() {
        val calls = mutableListOf<String>()
        val sink = HookDispatchSink(
            xposedSink = namedSink("xposed", calls),
            fallbackSink = namedSink("fallback", calls),
            bridgeSink = LogSink { error("bridge unavailable") },
        )

        sink.emit(event())

        assertEquals(listOf("xposed"), calls)
    }

    private fun namedSink(name: String, calls: MutableList<String>): LogSink {
        return LogSink { calls += name }
    }

    private fun event(): LogEvent {
        return LogEventBuilder(LogSource.HOOK).build(
            context = LogContext(),
            level = LogLevel.INFO,
            event = "hook.install.succeeded",
            message = null,
            throwable = null,
            fields = emptyMap(),
        )
    }
}
