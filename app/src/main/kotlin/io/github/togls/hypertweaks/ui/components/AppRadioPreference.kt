package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

@Composable
fun AppRadioPreference(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    RadioButtonPreference(
        title = title,
        summary = summary,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        radioButtonLocation = RadioButtonLocation.End,
        modifier = modifier,
    )
}
