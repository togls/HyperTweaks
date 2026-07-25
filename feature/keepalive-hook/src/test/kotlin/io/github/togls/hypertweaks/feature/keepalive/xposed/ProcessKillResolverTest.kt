package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.feature.keepalive.policy.ProcessKillGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessKillResolverTest {
    @Test
    fun `ams resolver only accepts known names with package parameters`() {
        val targets = ProcessKillResolver.resolveAms(ActivityManagerFixture::class.java)

        assertEquals(
            setOf(ProcessKillGroup.AmsBackground, ProcessKillGroup.AmsAggressive),
            targets.map(ProcessKillTarget::group).toSet(),
        )
        assertEquals(2, targets.size)
    }

    @Test
    fun `process record resolver requires safe kill shape`() {
        val targets = ProcessKillResolver.resolveProcessRecord(ProcessRecordFixture::class.java)

        assertEquals(1, targets.size)
        assertTrue(targets.single().packageFromReceiver)
        assertEquals("killLocked", targets.single().method.name)
    }

    @Suppress("UNUSED_PARAMETER")
    private class ActivityManagerFixture {
        fun killBackgroundProcesses(packageName: String): Boolean = true
        fun killBackgroundProcesses(processId: Int): Boolean = true
        fun forceStopPackage(packageName: String) = Unit
        fun unrelated(packageName: String) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    private class ProcessRecordFixture {
        fun killLocked(reason: String, reasonCode: Int, noisy: Boolean) = Unit
        fun killLocked(reasonCode: Int) = Unit
        fun kill(reason: String): Boolean = true
    }
}
