package io.github.togls.hypertweaks.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.togls.hypertweaks.ui.theme.HyperTweaksMiuixTheme

@Preview(showBackground = true)
@Composable
private fun MiuixAdaptersPreview() {
    HyperTweaksMiuixTheme(dynamicColor = false) {
        Column {
            AppPreferenceGroup(title = "Settings") {
                AppSwitchPreference(
                    title = "Feature",
                    summary = "Feature summary",
                    checked = true,
                    onCheckedChange = {},
                )
                AppDropdownPreference(
                    title = "Mode",
                    options = listOf("Automatic", "Manual"),
                    selected = "Automatic",
                    optionLabel = { it },
                    onSelectedChange = {},
                )
                AppClickablePreference(
                    title = "Details",
                    value = "Current",
                    onClick = {},
                )
            }
        }
    }
}
