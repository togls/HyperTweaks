package io.github.togls.hypertweaks.feature.keepalive.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalPackageGuardTest {
    @Test
    fun `critical packages and processes are always rejected`() {
        assertTrue(CriticalPackageGuard.isCritical("system_server"))
        assertTrue(CriticalPackageGuard.isCritical("com.android.systemui:screenshot"))
        assertTrue(CriticalPackageGuard.isCritical("zygote64"))
    }

    @Test
    fun `ordinary user package is not critical`() {
        assertFalse(CriticalPackageGuard.isCritical("com.example.app"))
    }
}
