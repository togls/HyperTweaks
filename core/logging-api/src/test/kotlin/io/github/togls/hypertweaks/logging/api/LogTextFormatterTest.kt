package io.github.togls.hypertweaks.logging.api

import org.junit.Assert.assertTrue
import org.junit.Test

class LogTextFormatterTest {
    @Test
    fun `formatter includes structured and diagnostic content`() {
        val event = LogEventBuilder(LogSource.HOOK).build(
            context = LogContext(tag = "HookRegistry"),
            level = LogLevel.ERROR,
            event = "hook.install.failed",
            message = "install failed",
            throwable = IllegalArgumentException("bad target"),
            fields = mapOf("target" to "system_server"),
        )

        val text = LogTextFormatter.format(event)

        assertTrue(text.contains("[HookRegistry]"))
        assertTrue(text.contains("hook.install.failed"))
        assertTrue(text.contains("target=system_server"))
        assertTrue(text.contains("IllegalArgumentException"))
    }
}
