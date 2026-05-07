package io.github.togls.hypertweaks.feature.ime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NavBarLayoutConfigTest {

    @Test
    fun `toHandleLayout should use default layout by default`() {
        val config = NavBarLayoutConfig()

        assertEquals(
            NavBarLayoutConfig.DefaultHandleLayout,
            config.toHandleLayout(),
        )
    }

    @Test
    fun `toHandleLayout should render selected start and end buttons`() {
        val config = NavBarLayoutConfig(
            start = NavBarButton.ImePicker,
            end = NavBarButton.Back,
        )

        assertEquals(
            "ime_switcher[70AC];home_handle;back[70AC]",
            config.toHandleLayout(),
        )
    }
}