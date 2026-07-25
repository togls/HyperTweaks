package io.github.togls.hypertweaks.logging.app.ingest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogIngestQuotaTest {
    @Test
    fun `quota rejects excess events per uid and resets after window`() {
        var nowMillis = 1_000L
        val quota = LogIngestQuota(
            maximumEventsPerWindow = 4,
            windowMillis = 60_000L,
            nowMillis = { nowMillis },
        )

        assertTrue(quota.tryAcquire(uid = 10, eventCount = 3))
        assertFalse(quota.tryAcquire(uid = 10, eventCount = 2))
        assertTrue(quota.tryAcquire(uid = 11, eventCount = 4))

        nowMillis += 60_000L

        assertTrue(quota.tryAcquire(uid = 10, eventCount = 4))
    }
}
