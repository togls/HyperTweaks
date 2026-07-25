package io.github.togls.hypertweaks.core.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessHookInstallGuardTest {
    @Test
    fun failedRetryableInstallCanStartAfterBackoff() {
        var nowMillis = 1_000L
        val guard = ProcessHookInstallGuard(
            nowMillis = { nowMillis },
            retryBackoffMillis = longArrayOf(100L),
        )
        val key = key()

        assertTrue(guard.tryStart(key))
        val delayMillis = guard.markFailed(
            key = key,
            retryable = true,
            failureStage = "feature_install",
            failureMessage = "temporary",
        )

        assertEquals(100L, delayMillis)
        assertEquals(HookInstallState.FAILED_RETRYABLE, guard.state(key))
        assertTrue(!guard.tryStart(key))
        nowMillis += 100L
        assertTrue(guard.tryStart(key))
        assertEquals(2, guard.record(key).attemptCount)
    }

    @Test
    fun retryBudgetExhaustionBecomesTerminal() {
        var nowMillis = 1_000L
        val guard = ProcessHookInstallGuard(
            nowMillis = { nowMillis },
            retryBackoffMillis = longArrayOf(100L),
        )
        val key = key()
        assertTrue(guard.tryStart(key))
        guard.markFailed(key, true, "feature_install", "first")
        nowMillis += 100L
        assertTrue(guard.tryStart(key))

        val nextDelay = guard.markFailed(key, true, "feature_install", "second")

        assertEquals(null, nextDelay)
        assertEquals(HookInstallState.FAILED_TERMINAL, guard.state(key))
        assertEquals("second", guard.record(key).lastFailureMessage)
    }

    private fun key(): HookInstallKey {
        return HookInstallKey(
            featureId = "keepalive.process-kill",
            packageName = "system_server",
            processName = "system_server",
            classLoaderIdentity = 1,
            targetId = "system_server",
        )
    }
}
