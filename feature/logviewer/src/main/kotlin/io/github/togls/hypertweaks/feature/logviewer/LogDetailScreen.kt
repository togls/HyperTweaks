package io.github.togls.hypertweaks.feature.logviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogTextFormatter
import java.text.DateFormat
import java.util.Date

@Composable
fun LogDetailDialog(
    event: LogEvent,
    onDismiss: () -> Unit,
    onFilterTag: () -> Unit,
    onFilterSession: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_detail_title)) },
        text = { LogDetailScreen(event, onFilterTag, onFilterSession) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Suppress("DEPRECATION")
@Composable
fun LogDetailScreen(
    event: LogEvent,
    onFilterTag: () -> Unit,
    onFilterSession: (() -> Unit)?,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailField(stringResource(R.string.field_time), formatTime(event.timestampMillis))
        DetailField(stringResource(R.string.field_source), event.source.name)
        DetailField(stringResource(R.string.field_level), event.level.name)
        DetailField(stringResource(R.string.field_tag), event.tag)
        DetailField(stringResource(R.string.field_event), event.event)
        DetailField(stringResource(R.string.field_package), event.packageName)
        DetailField(stringResource(R.string.field_process), event.processName)
        DetailField("PID / TID", "${event.pid ?: "-"} / ${event.tid ?: "-"}")
        DetailField(stringResource(R.string.field_session), event.sessionId)
        DetailField(stringResource(R.string.field_message), event.message)
        DetailField(stringResource(R.string.field_fields), event.fields.entries.joinToString("\n") { "${it.key}=${it.value}" })
        DetailField(stringResource(R.string.field_throwable), event.throwableType)
        DetailField(stringResource(R.string.field_throwable_message), event.throwableMessage)
        DetailField(stringResource(R.string.field_stack_trace), event.stackTrace)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CopyButton(R.string.action_copy_message) { clipboard.setText(AnnotatedString(event.message.orEmpty())) }
            CopyButton(R.string.action_copy_event) { clipboard.setText(AnnotatedString(LogTextFormatter.format(event))) }
            CopyButton(R.string.action_copy_stack) { clipboard.setText(AnnotatedString(event.stackTrace.orEmpty())) }
            CopyButton(R.string.action_filter_tag, onFilterTag)
            if (onFilterSession != null) CopyButton(R.string.action_filter_session, onFilterSession)
        }
    }
}

@Composable
private fun DetailField(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Text(value, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CopyButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

private fun formatTime(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(timestamp))
}
