package io.github.togls.miaospime.data

enum class NavBarButton(
    val value: String,
    val layoutValue: String,
    val displayName: String,
) {
    Back(
        value = "back",
        layoutValue = "back",
        displayName = "Hide Ime",
    ),

    ImeSwitcher(
        value = "ime_switcher",
        layoutValue = "ime_switcher",
        displayName = "Ime Switcher",
    ),

    ImePicker(
        value = "ime_picker",
        layoutValue = "ime_switcher",
        displayName = "Input Method Picker",
    );

    companion object {
        fun fromValue(value: String?): NavBarButton {
            return entries.firstOrNull { it.value == value } ?: Back
        }
    }
}