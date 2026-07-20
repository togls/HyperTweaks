package io.github.togls.hypertweaks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun HyperTweaksMiuixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorSchemeMode = if (dynamicColor) {
        ColorSchemeMode.MonetSystem
    } else {
        ColorSchemeMode.System
    }
    val controller = remember(colorSchemeMode, darkTheme) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            isDark = darkTheme,
        )
    }

    MiuixTheme(
        controller = controller,
        content = content,
    )
}
