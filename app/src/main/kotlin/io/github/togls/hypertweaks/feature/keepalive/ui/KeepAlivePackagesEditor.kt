package io.github.togls.hypertweaks.feature.keepalive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.ui.components.AppButton
import io.github.togls.hypertweaks.ui.components.AppInfoPreference
import io.github.togls.hypertweaks.ui.components.AppSpacing
import io.github.togls.hypertweaks.ui.components.AppTextField

@Composable
fun KeepAlivePackagesEditor(
    packagesText: String,
    invalidPackages: List<String>,
    enabled: Boolean,
    onPackagesTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val validationMessage = invalidPackagesMessage(invalidPackages)

    Column(modifier = modifier) {
        AppInfoPreference(
            title = stringResource(R.string.keep_alive_title),
            summary = stringResource(R.string.keep_alive_description),
        )

        AppTextField(
            value = packagesText,
            onValueChange = onPackagesTextChange,
            label = stringResource(R.string.keep_alive_packages_label),
            enabled = enabled,
            minLines = 4,
            maxLines = 8,
            isError = invalidPackages.isNotEmpty(),
            supportingText = validationMessage,
            modifier = Modifier.padding(horizontal = AppSpacing.large),
        )

        AppButton(
            text = stringResource(R.string.action_save_keep_alive_packages),
            enabled = enabled,
            onClick = {
                focusManager.clearFocus()
                onSaveClick()
            },
            modifier = Modifier.padding(
                start = AppSpacing.large,
                top = AppSpacing.medium,
                end = AppSpacing.large,
                bottom = AppSpacing.large,
            ),
        )
    }
}

@Composable
private fun invalidPackagesMessage(
    invalidPackages: List<String>,
): String? {
    return invalidPackages.takeIf { it.isNotEmpty() }?.let {
        stringResource(
            R.string.keep_alive_invalid_packages_hint,
            it.joinToString(),
        )
    }
}
