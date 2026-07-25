package io.github.togls.hypertweaks.logging.app.ingest

import java.util.concurrent.ConcurrentHashMap

internal class LogIngestQuota(
    private val maximumEventsPerWindow: Int = DefaultMaximumEventsPerWindow,
    private val windowMillis: Long = DefaultWindowMillis,
    private val nowMillis: () -> Long = { System.nanoTime() / NanosPerMillis },
) {
    private val windows = ConcurrentHashMap<Int, Window>()

    fun tryAcquire(uid: Int, eventCount: Int): Boolean {
        require(eventCount >= 0) { "Event count must not be negative" }
        if (eventCount == 0) return true
        val now = nowMillis()
        var accepted = false
        windows.compute(uid) { _, currentWindow ->
            val activeWindow = currentWindow
                ?.takeIf { now - it.startedAtMillis < windowMillis }
                ?: Window(startedAtMillis = now, acceptedEvents = 0)
            if (activeWindow.acceptedEvents + eventCount <= maximumEventsPerWindow) {
                accepted = true
                activeWindow.copy(acceptedEvents = activeWindow.acceptedEvents + eventCount)
            } else {
                activeWindow
            }
        }
        return accepted
    }

    private data class Window(
        val startedAtMillis: Long,
        val acceptedEvents: Int,
    )

    private companion object {
        const val DefaultMaximumEventsPerWindow = 2_048
        const val DefaultWindowMillis = 60_000L
        const val NanosPerMillis = 1_000_000L
    }
}
