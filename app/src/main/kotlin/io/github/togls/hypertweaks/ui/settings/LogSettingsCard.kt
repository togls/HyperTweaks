package io.github.togls.hypertweaks.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.ui.components.AppClickablePreference
import io.github.togls.hypertweaks.ui.components.AppDropdownPreference
import io.github.togls.hypertweaks.ui.components.AppPreferenceGroup

@Composable
fun LogSettingsCard(
    mode: LogMode,
    serviceConnected: Boolean,
    onModeChange: (LogMode) -> Unit,
    onViewLogsClick: () -> Unit,
) {
    AppPreferenceGroup(title = stringResource(R.string.logging_title)) {
        AppDropdownPreference(
            title = stringResource(R.string.logging_mode_title),
            summary = stringResource(mode.summaryResource()),
            options = LogMode.entries,
            selected = mode,
            optionLabel = { it.name },
            onSelectedChange = onModeChange,
            enabled = serviceConnected,
        )
        AppClickablePreference(
            title = stringResource(R.string.logging_view_logs),
            summary = stringResource(R.string.logging_view_logs_summary),
            onClick = onViewLogsClick,
        )
    }
}

private fun LogMode.summaryResource(): Int = when (this) {
    LogMode.OFF -> R.string.logging_mode_off_summary
    LogMode.BASIC -> R.string.logging_mode_basic_summary
    LogMode.DEBUG -> R.string.logging_mode_debug_summary
}
