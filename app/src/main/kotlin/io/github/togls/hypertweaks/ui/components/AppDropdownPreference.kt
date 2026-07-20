package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun <Option> AppDropdownPreference(
    title: String,
    options: List<Option>,
    selected: Option,
    optionLabel: @Composable (Option) -> String,
    onSelectedChange: (Option) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val labels = options.map { optionLabel(it) }

    OverlayDropdownPreference(
        items = labels,
        selectedIndex = selectedIndex,
        title = title,
        summary = summary,
        enabled = enabled,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let(onSelectedChange)
        },
        modifier = modifier,
    )
}
