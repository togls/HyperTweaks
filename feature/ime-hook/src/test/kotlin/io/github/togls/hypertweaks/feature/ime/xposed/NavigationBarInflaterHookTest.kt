package io.github.togls.hypertweaks.feature.ime.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationBarInflaterHookTest {
    @Test
    fun `identical layout does not replace system layout`() {
        val layout = "back[70AC];home_handle;ime_switcher[70AC]"

        assertNull(resolveNavigationBarLayoutReplacement(layout, layout))
        assertEquals("same_as_original", navigationBarLayoutBypassReason(layout, layout))
    }

    @Test
    fun `blank configured layout does not replace system layout`() {
        assertNull(resolveNavigationBarLayoutReplacement("  ", "system_default"))
        assertEquals(
            "blank_configuration",
            navigationBarLayoutBypassReason("  ", "system_default"),
        )
    }

    @Test
    fun `missing original layout does not provide a replacement`() {
        assertNull(resolveNavigationBarLayoutReplacement("configured", null))
        assertEquals(
            "original_layout_unavailable",
            navigationBarLayoutBypassReason("configured", null),
        )
    }

    @Test
    fun `different configured layout replaces system layout`() {
        assertEquals(
            "ime_switcher[70AC];home_handle;back[70AC]",
            resolveNavigationBarLayoutReplacement(
                configuredLayout = "ime_switcher[70AC];home_handle;back[70AC]",
                originalLayout = "back[70AC];home_handle;ime_switcher[70AC]",
            ),
        )
        assertNull(
            navigationBarLayoutBypassReason(
                configuredLayout = "ime_switcher[70AC];home_handle;back[70AC]",
                originalLayout = "back[70AC];home_handle;ime_switcher[70AC]",
            ),
        )
    }
}
