package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEventBuilder
import io.github.togls.hypertweaks.logging.api.LogIngestResult
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookBatchDispatcherTest {
    @Test
    fun `recovery request bypasses pending backoff`() {
        val attemptCount = AtomicInteger()
        val recoveryAttempted = CountDownLatch(1)
        val dispatcher = HookBatchDispatcher(
            buffer = HookEventBuffer(),
            transport = HookBatchTransport { events ->
                if (attemptCount.incrementAndGet() == 1) {
                    LogIngestResult(0, events.size, true, "provider_unavailable")
                } else {
                    recoveryAttempted.countDown()
                    LogIngestResult(events.size, 0, false, null)
                }
            },
            executor = Executors.newSingleThreadScheduledExecutor(),
            clock = { 10_000L },
        )

        dispatcher.use {
            dispatcher.start()
            dispatcher.enqueue(testEvent())
            dispatcher.flushSafely()
            assertEquals(1, attemptCount.get())

            dispatcher.requestFlush()

            assertTrue(recoveryAttempted.await(2, TimeUnit.SECONDS))
            assertEquals(2, attemptCount.get())
        }
    }

    @Test
    fun `recovery request after close is ignored`() {
        val dispatcher = HookBatchDispatcher(
            buffer = HookEventBuffer(),
            transport = HookBatchTransport { events ->
                LogIngestResult(events.size, 0, false, null)
            },
            executor = Executors.newSingleThreadScheduledExecutor(),
        )

        dispatcher.start()
        dispatcher.close()

        dispatcher.requestFlush()
    }

    private fun testEvent() = LogEventBuilder(LogSource.HOOK).build(
        context = LogContext(),
        level = LogLevel.INFO,
        event = "hook.callback.completed",
        message = null,
        throwable = null,
        fields = emptyMap(),
    )
}
