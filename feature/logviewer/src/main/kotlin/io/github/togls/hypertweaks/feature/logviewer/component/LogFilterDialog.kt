package io.github.togls.hypertweaks.feature.logviewer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.feature.logviewer.LogFilterState
import io.github.togls.hypertweaks.feature.logviewer.LogTimeRange
import io.github.togls.hypertweaks.feature.logviewer.R
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource

@Composable
fun LogFilterDialog(
    current: LogFilterState,
    onDismiss: () -> Unit,
    onApply: (LogFilterState) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            FilterContent(draft, onChange = { draft = it })
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft); onDismiss() }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = { onReset(); onDismiss() }) {
                Text(stringResource(R.string.action_reset))
            }
        },
    )
}

@Composable
private fun FilterContent(state: LogFilterState, onChange: (LogFilterState) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.filter_source))
        SourceChips(state, onChange)
        Text(stringResource(R.string.filter_levels))
        LevelChips(state, onChange)
        FilterTextFields(state, onChange)
        Text(stringResource(R.string.filter_time_range))
        TimeRangeChips(state, onChange)
    }
}

@Composable
private fun SourceChips(state: LogFilterState, onChange: (LogFilterState) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceChip(null, R.string.filter_all, state, onChange)
        SourceChip(LogSource.APP, R.string.filter_app, state, onChange)
        SourceChip(LogSource.HOOK, R.string.filter_hook, state, onChange)
    }
}

@Composable
private fun SourceChip(
    source: LogSource?,
    label: Int,
    state: LogFilterState,
    onChange: (LogFilterState) -> Unit,
) {
    FilterChip(
        selected = state.source == source,
        onClick = { onChange(state.copy(source = source)) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun LevelChips(state: LogFilterState, onChange: (LogFilterState) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogLevel.entries.forEach { level ->
            FilterChip(
                selected = level in state.levels,
                onClick = {
                    val levels = if (level in state.levels) state.levels - level else state.levels + level
                    onChange(state.copy(levels = levels))
                },
                label = { Text(level.name) },
            )
        }
    }
}

@Composable
private fun FilterTextFields(state: LogFilterState, onChange: (LogFilterState) -> Unit) {
    FilterTextField(state.packageName, R.string.field_package) { onChange(state.copy(packageName = it)) }
    FilterTextField(state.tag, R.string.field_tag) { onChange(state.copy(tag = it)) }
    FilterTextField(state.event, R.string.field_event) { onChange(state.copy(event = it)) }
    FilterTextField(state.sessionId, R.string.field_session) { onChange(state.copy(sessionId = it)) }
    FilterTextField(state.keyword, R.string.filter_keyword) { onChange(state.copy(keyword = it)) }
}

@Composable
private fun FilterTextField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
    )
}

@Composable
private fun TimeRangeChips(state: LogFilterState, onChange: (LogFilterState) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogTimeRange.entries.forEach { range ->
            FilterChip(
                selected = state.timeRange == range,
                onClick = { onChange(state.copy(timeRange = range)) },
                label = { Text(stringResource(range.label())) },
            )
        }
    }
}

private fun LogTimeRange.label(): Int = when (this) {
    LogTimeRange.ALL -> R.string.filter_all_time
    LogTimeRange.DAY -> R.string.filter_last_day
    LogTimeRange.WEEK -> R.string.filter_last_week
    LogTimeRange.MONTH -> R.string.filter_last_month
}
