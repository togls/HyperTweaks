package io.github.togls.hypertweaks.logging.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryBackoffTest {
    @Test
    fun `backoff follows bounded schedule and resets`() {
        val backoff = RetryBackoff()

        assertEquals(
            listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 30_000L),
            List(6) { backoff.nextDelayMillis() },
        )

        backoff.reset()
        assertEquals(1_000L, backoff.nextDelayMillis())
    }
}
