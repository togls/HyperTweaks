package io.github.togls.hypertweaks.feature.keepalive

import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveHookFeatureProviderTest {
    @Test
    fun `system server safety switch excludes every keepalive feature`() {
        val features = KeepAliveHookFeatureProvider(
            systemServerFeatureEnabled = { false },
        ).features()

        assertTrue(features.isNotEmpty())
        assertTrue(features.none { feature -> feature.supports(systemServerEnvironment()) })
    }

    @Test
    fun `enabled safety switch keeps system server features available`() {
        val features = KeepAliveHookFeatureProvider(
            systemServerFeatureEnabled = { true },
        ).features()

        assertFalse(features.isEmpty())
        assertTrue(features.all { feature -> feature.supports(systemServerEnvironment()) })
    }

    private fun systemServerEnvironment(): HookEnvironment {
        return HookEnvironment(
            packageName = "android",
            processName = "system_server",
            classLoader = javaClass.classLoader!!,
            sdkInt = 35,
            sessionId = "test-session",
            isSystemServer = true,
        )
    }
}
