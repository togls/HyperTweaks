package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun <Option> AppDropdownPreference(
    title: String,
    summary: String? = null,
    options: List<Option>,
    selected: Option,
    optionLabel: @Composable (Option) -> String,
    enabled: Boolean = true,
    onSelectedChange: (Option) -> Unit,
    modifier: Modifier = Modifier,
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
