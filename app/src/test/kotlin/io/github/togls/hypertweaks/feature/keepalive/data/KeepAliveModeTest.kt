package io.github.togls.hypertweaks.feature.keepalive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAliveModeTest {

    @Test
    fun `fromValue should return matching mode`() {
        assertEquals(
            KeepAliveMode.Conservative,
            KeepAliveMode.fromValue("conservative"),
        )

        assertEquals(
            KeepAliveMode.Aggressive,
            KeepAliveMode.fromValue("aggressive"),
        )

        assertEquals(
            KeepAliveMode.OomOnly,
            KeepAliveMode.fromValue("oom_only"),
        )
    }

    @Test
    fun `fromValue should fallback to default mode`() {
        assertEquals(
            KeepAliveMode.Default,
            KeepAliveMode.fromValue(null),
        )

        assertEquals(
            KeepAliveMode.Default,
            KeepAliveMode.fromValue("unknown"),
        )
    }
}