package io.github.togls.miaospime.data

enum class NavBarButton(
    val value: String,
    val displayName: String,
) {
    Back(
        value = "back",
        displayName = "Hide Ime",
    ),

    ImeSwitcher(
        value = "ime_switcher",
        displayName = "Ime Switcher",
    );

    companion object {
        fun fromValue(value: String?): NavBarButton {
            return entries.firstOrNull { it.value == value } ?: Back
        }
    }
}