package io.github.togls.hypertweaks.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val colors = if (isError) {
        TextFieldDefaults.textFieldColors(
            backgroundColor = MiuixTheme.colorScheme.errorContainer,
            labelColor = MiuixTheme.colorScheme.onErrorContainer,
            borderColor = MiuixTheme.colorScheme.error,
        )
    } else {
        TextFieldDefaults.textFieldColors()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            enabled = enabled,
            minLines = minLines,
            maxLines = maxLines,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppSpacing.large),
            )
        }
    }
}
