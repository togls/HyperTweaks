package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.BasicComponent

@Composable
fun AppInfoPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    BasicComponent(
        title = title,
        summary = summary,
        modifier = modifier,
    )
}
