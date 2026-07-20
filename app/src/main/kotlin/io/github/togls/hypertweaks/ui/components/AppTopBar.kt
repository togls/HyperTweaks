package io.github.togls.hypertweaks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
internal fun AppTopBar(
    title: String,
    subtitle: String?,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = title,
        largeTitle = title,
        subtitle = subtitle.orEmpty(),
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}
