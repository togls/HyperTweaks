package io.github.togls.hypertweaks.feature.logviewer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import java.text.DateFormat
import java.util.Date

@Composable
fun LogEntryCard(event: LogEvent, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(event.timestampMillis), style = MaterialTheme.typography.labelSmall)
                Text(
                    "${event.level.name} · ${event.source.name}",
                    color = event.level.color(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(event.tag.ifBlank { "HyperTweaks" }, style = MaterialTheme.typography.titleSmall)
            Text(event.event, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            val target = listOfNotNull(event.packageName, event.processName).distinct().joinToString(" / ")
            if (target.isNotBlank()) Text(target, style = MaterialTheme.typography.labelSmall)
            event.message?.lineSequence()?.firstOrNull()?.let { firstLine ->
                Text(firstLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LogLevel.color() = when (this) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

private fun formatTime(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp))
}
