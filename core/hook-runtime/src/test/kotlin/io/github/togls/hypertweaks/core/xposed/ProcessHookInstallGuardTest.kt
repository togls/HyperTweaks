package io.github.togls.hypertweaks.core.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessHookInstallGuardTest {
    @Test
    fun failedInstallCannotStartAgainInSameProcess() {
        val guard = ProcessHookInstallGuard()
        val key = HookInstallKey(
            featureId = "keepalive.process-kill",
            packageName = "system_server",
            processName = "system_server",
            classLoaderIdentity = 1,
            targetId = "system_server",
        )

        assertTrue(guard.tryStart(key))
        guard.markFailed(key)

        assertFalse(guard.tryStart(key))
        assertEquals(HookInstallState.FAILED, guard.state(key))
    }
}
