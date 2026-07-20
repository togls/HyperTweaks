package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

@Composable
fun AppRadioPreference(
    title: String,
    summary: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
