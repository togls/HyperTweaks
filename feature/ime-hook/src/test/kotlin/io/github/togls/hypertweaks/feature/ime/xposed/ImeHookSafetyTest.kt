package io.github.togls.hypertweaks.feature.ime.xposed

import io.github.togls.hypertweaks.feature.ime.eventLogger
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import io.github.togls.hypertweaks.logging.api.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeHookSafetyTest {
    @Test
    fun `callback failure returns original value and logs error`() {
        val events = mutableListOf<LogEvent>()
        val originalValue = Any()

        val actualValue = preserveOriginalOnFailure(
            log = eventLogger(events),
            event = "test.callback",
            originalValue = originalValue,
        ) {
            error("callback failed")
        }

        assertSame(originalValue, actualValue)
        assertEquals(
            listOf("hook.callback.entered", "hook.callback.failed"),
            events.map(LogEvent::event),
        )
        assertTrue(events.last().message.orEmpty().contains("test.callback"))
    }

    @Test
    fun `successful callback returns replacement value`() {
        val events = mutableListOf<LogEvent>()
        val actualValue = preserveOriginalOnFailure(
            log = eventLogger(events),
            event = "test.callback",
            originalValue = "original",
        ) {
            "replacement"
        }

        assertEquals("replacement", actualValue)
        assertEquals("hook.callback.transformed", events.last().event)
    }

    @Test
    fun `explicit callback bypass records reason`() {
        val events = mutableListOf<LogEvent>()

        logImeCallbackBypassed(
            log = eventLogger(events),
            subtarget = "ime_switch",
            reason = "disabled",
        )

        assertEquals(
            listOf("hook.callback.entered", "hook.callback.bypassed"),
            events.map(LogEvent::event),
        )
        assertEquals("disabled", events.last().fields["reason"])
    }

    @Test
    fun `target install failure is explicit`() {
        val events = mutableListOf<LogEvent>()
        val logger = eventLogger(events)
        logImeTargetResolveStarted(logger, "dead_zone")
        val result = installImeTarget(
            target = "dead_zone",
            log = logger,
        ) {
            error("install failed")
        }

        assertTrue(result is ImeTargetInstallResult.Failed)
        assertEquals("dead_zone", result.target)
        assertEquals(
            listOf(
                "target.resolve.started",
                "target.resolve.succeeded",
                "hook.install.failed",
            ),
            events.map(LogEvent::event),
        )
    }

    @Test
    fun `missing target logs explicit resolution failure`() {
        val events = mutableListOf<LogEvent>()
        val logger = eventLogger(events)
        logImeTargetResolveStarted(logger, "bottom_manager")

        val result = skipImeTarget(
            target = "bottom_manager",
            reason = "MIUI class not found",
            log = logger,
        )

        assertTrue(result is ImeTargetInstallResult.Skipped)
        assertEquals(
            listOf("target.resolve.started", "target.resolve.failed"),
            events.map(LogEvent::event),
        )
        assertEquals("bottom_manager", events.last().fields["subtarget"])
        assertEquals("MIUI class not found", events.last().fields["reason"])
    }
}
