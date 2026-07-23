package io.github.togls.hypertweaks.core.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookSettingsSnapshotTest {
    @Test
    fun unavailableStateUsesDisabledDefaults() {
        val snapshot = HookSettingsState.Unavailable(IllegalStateException("offline"))
            .snapshotOrDisabled()

        assertFalse(snapshot.isEnabled("ime_enabled"))
        assertEquals(HookSettingsSnapshot.DefaultKeepAliveMode, snapshot.keepAliveMode)
        assertEquals("", snapshot.keepAlivePackages)
    }

    @Test
    fun readySnapshotKeepsAllValuesTogether() {
        val snapshot = HookSettingsSnapshot(
            enabledPreferenceKeys = setOf("ime_enabled"),
            navBarLayoutHandle = "back[70AC];home_handle;ime_picker[70AC]",
            keepAliveMode = "conservative",
            keepAlivePackages = "org.example.app",
        )

        assertTrue(snapshot.isEnabled("ime_enabled"))
        assertEquals("conservative", snapshot.keepAliveMode)
        assertEquals("org.example.app", snapshot.keepAlivePackages)
    }
}
