package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun AppDialog(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = visible,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}
