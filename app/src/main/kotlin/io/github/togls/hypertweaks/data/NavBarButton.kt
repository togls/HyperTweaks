package io.github.togls.hypertweaks.data

import androidx.annotation.StringRes
import io.github.togls.hypertweaks.R

enum class NavBarButton(
    val value: String,
    val layoutValue: String,
    @field:StringRes val displayNameRes: Int
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