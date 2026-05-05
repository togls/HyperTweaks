package io.github.togls.miaospime.data

import androidx.annotation.StringRes
import io.github.togls.miaospime.R

enum class NavBarButton(
    val value: String,
    val layoutValue: String,
    @StringRes val displayNameRes: Int
) {
    Back(
        value = "back",
        layoutValue = "back",
        displayNameRes = R.string.nav_button_hide_ime,
    ),

    ImeSwitcher(
        value = "ime_switcher",
        layoutValue = "ime_switcher",
        displayNameRes = R.string.nav_button_ime_switcher,
    ),

    ImePicker(
        value = "ime_picker",
        layoutValue = "ime_switcher",
        displayNameRes = R.string.nav_button_ime_picker,
    );

    companion object {
        fun fromValue(value: String?): NavBarButton {
            return entries.firstOrNull { it.value == value } ?: Back
        }
    }
}