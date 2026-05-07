package io.github.togls.hypertweaks.feature.ime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NavBarButtonTest {

    @Test
    fun `fromValue should return matching button`() {
        assertEquals(
            NavBarButton.ImeSwitcher,
            NavBarButton.fromValue("ime_switcher"),
        )

        assertEquals(
            NavBarButton.ImePicker,
            NavBarButton.fromValue("ime_picker"),
        )
    }

    @Test
    fun `fromValue should fallback to Back when value is unknown`() {
        assertEquals(
            NavBarButton.Back,
            NavBarButton.fromValue("unknown"),
        )

        assertEquals(
            NavBarButton.Back,
            NavBarButton.fromValue(null),
        )
    }

    @Test
    fun `ImePicker should reuse ime switcher layout value`() {
        assertEquals(
            "ime_switcher",
            NavBarButton.ImePicker.layoutValue,
        )
    }
}