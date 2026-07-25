package io.github.togls.hypertweaks.feature.keepalive.policy

import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAlivePolicyTest {
    @Test
    fun `empty configuration installs no interception`() {
        val policy = policy(KeepAliveMode.Full, "")

        assertFalse(policy.shouldInstallProcessKillHooks())
        assertFalse(policy.shouldInstallOomAdjHooks())
    }

    @Test
    fun `oom only never blocks normal kill`() {
        val policy = policy(KeepAliveMode.OomOnly)

        val decision = policy.decideProcessKill(
            "com.example.app",
            ProcessKillGroup.ProcessRecordKill,
        )

        assertTrue(decision is Decision.Allow)
        assertEquals("oom_only_preserves_kill", decision.reason)
        assertFalse(policy.shouldInstallProcessKillHooks())
        assertTrue(policy.shouldInstallOomAdjHooks())
    }

    @Test
    fun `audit mode records without changing kill or oom`() {
        val policy = policy(KeepAliveMode.Audit)

        assertTrue(
            policy.decideProcessKill(
                "com.example.app",
                ProcessKillGroup.AmsBackground,
            ) is Decision.Audit,
        )
        assertTrue(
            policy.decideOomAdj("com.example.app", 900, 200) is Decision.Audit,
        )
    }

    @Test
    fun `conservative mode only blocks background cleanup groups`() {
        val policy = policy(KeepAliveMode.Conservative)

        assertTrue(
            policy.decideProcessKill(
                "com.example.app",
                ProcessKillGroup.MiuiSmartPower,
            ) is Decision.Block,
        )
        assertTrue(
            policy.decideProcessKill(
                "com.example.app",
                ProcessKillGroup.ProcessRecordKill,
            ) is Decision.Allow,
        )
    }

    @Test
    fun `full mode only blocks explicit noncritical package`() {
        val policy = policy(KeepAliveMode.Full)

        assertTrue(
            policy.decideProcessKill(
                "com.example.app:worker",
                ProcessKillGroup.ProcessRecordKill,
            ) is Decision.Block,
        )
        assertTrue(
            policy.decideProcessKill(
                "com.other.app",
                ProcessKillGroup.ProcessRecordKill,
            ) is Decision.Allow,
        )
        assertTrue(
            policy.decideProcessKill(
                "system_server",
                ProcessKillGroup.ProcessRecordKill,
            ) is Decision.Allow,
        )
    }

    @Test
    fun `configuration update replaces mode and package set atomically`() {
        val policy = policy(KeepAliveMode.Full)
        policy.update(settings(KeepAliveMode.OomOnly, "com.updated.app"))

        assertTrue(
            policy.decideProcessKill(
                "com.example.app",
                ProcessKillGroup.AmsBackground,
            ) is Decision.Allow,
        )
        val updatedDecision = policy.decideOomAdj("com.updated.app", 900, 200)
        assertTrue(updatedDecision is Decision.Clamp)
    }

    private fun policy(
        mode: KeepAliveMode,
        packages: String = "com.example.app",
    ): KeepAlivePolicy {
        return KeepAlivePolicy(settings(mode, packages))
    }

    private fun settings(mode: KeepAliveMode, packages: String): HookSettingsSnapshot {
        return HookSettingsSnapshot(
            keepAliveMode = mode.value,
            keepAlivePackages = packages,
        )
    }
}
