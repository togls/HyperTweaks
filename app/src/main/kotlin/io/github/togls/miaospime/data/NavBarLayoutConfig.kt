package io.github.togls.miaospime.data

data class NavBarLayoutConfig(
    val start: NavBarButton = NavBarButton.Back,
    val end: NavBarButton = NavBarButton.ImeSwitcher,
) {
    fun toHandleLayout(): String {
        val startValue = start.layoutValue
        val endValue = end.layoutValue

        if (startValue.isNotBlank() && endValue.isNotBlank()) {
            return "$startValue[70AC];$HomeHandle;$endValue[70AC]"
        }

        return DefaultHandleLayout
    }

    fun usesImePicker(): Boolean {
        return start == NavBarButton.ImePicker || end == NavBarButton.ImePicker
    }

    companion object {
        const val HomeHandle = "home_handle"
        const val DefaultHandleLayout = "back[70AC];home_handle;ime_switcher[70AC]"
    }
}