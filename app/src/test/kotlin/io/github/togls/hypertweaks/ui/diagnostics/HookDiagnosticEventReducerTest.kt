package io.github.togls.hypertweaks.ui.diagnostics

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HookDiagnosticEventReducerTest {
    @Test
    fun `keeps latest install result for each feature and process`() {
        val events = listOf(
            event(1L, "hook.install.started", "ime.system_server", "system_server"),
            event(2L, "hook.install.succeeded", "ime.system_server", "system_server"),
            event(3L, "hook.install.failed", "keepalive.process_kill", "system_server"),
        )

        val result = HookDiagnosticEventReducer.reduce(events)

        assertEquals(2, result.featureInstalls.size)
        assertEquals("keepalive.process_kill", result.featureInstalls[0].featureId)
        assertEquals("failed", result.featureInstalls[0].status)
        assertEquals("succeeded", result.featureInstalls[1].status)
        assertEquals("hook.install.failed", result.failureStage?.event)
    }

    @Test
    fun `separates resolve and callback events`() {
        val events = listOf(
            event(1L, "target.resolve.succeeded", "googlephotos.location", "photos"),
            event(2L, "hook.callback.transformed", "googlephotos.location", "photos"),
        )

        val result = HookDiagnosticEventReducer.reduce(events)

        assertEquals(listOf("target.resolve.succeeded"), result.resolveResults.map { it.event })
        assertEquals(listOf("hook.callback.transformed"), result.recentCallbacks.map { it.event })
        assertNull(result.failureStage)
    }

    private fun event(
        timestamp: Long,
        eventName: String,
        feature: String,
        process: String,
    ): LogEvent {
        return LogEvent(
            eventId = "$timestamp-$eventName",
            timestampMillis = timestamp,
            elapsedRealtimeMillis = timestamp,
            source = LogSource.HOOK,
            level = if (eventName.endsWith(".failed")) LogLevel.ERROR else LogLevel.INFO,
            tag = feature,
            event = eventName,
            message = null,
            packageName = "target.package",
            processName = process,
            pid = 100,
            tid = 101,
            sessionId = "session",
            fields = mapOf("feature" to feature),
            throwableType = null,
            throwableMessage = null,
            stackTrace = null,
        )
    }
}
