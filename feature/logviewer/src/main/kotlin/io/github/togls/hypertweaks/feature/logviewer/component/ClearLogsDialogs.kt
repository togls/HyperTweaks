package io.github.togls.hypertweaks.feature.logviewer.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.feature.logviewer.ClearLogsAction
import io.github.togls.hypertweaks.feature.logviewer.R

@Composable
fun ClearOptionsDialog(
    onDismiss: () -> Unit,
    onSelect: (ClearLogsAction?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_logs_title)) },
        text = {
            Column {
                ClearOption(R.string.clear_all) { onSelect(ClearLogsAction.ALL) }
                ClearOption(R.string.clear_filtered) { onSelect(ClearLogsAction.CURRENT_FILTER) }
                ClearOption(R.string.clear_older_7_days) { onSelect(ClearLogsAction.OLDER_THAN_7_DAYS) }
                ClearOption(R.string.clear_older_30_days) { onSelect(ClearLogsAction.OLDER_THAN_30_DAYS) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun ClearConfirmationDialog(
    action: ClearLogsAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_confirm_title)) },
        text = { Text(stringResource(action.confirmationMessage())) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ClearOption(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

private fun ClearLogsAction.confirmationMessage(): Int = when (this) {
    ClearLogsAction.ALL -> R.string.clear_confirm_all
    ClearLogsAction.CURRENT_FILTER -> R.string.clear_confirm_filtered
    ClearLogsAction.OLDER_THAN_7_DAYS -> R.string.clear_confirm_7_days
    ClearLogsAction.OLDER_THAN_30_DAYS -> R.string.clear_confirm_30_days
}
