package io.github.togls.hypertweaks.feature.logviewer

import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import io.github.togls.hypertweaks.logging.app.repository.LogQuery

data class LogFilterState(
    val source: LogSource? = null,
    val levels: Set<LogLevel> = LogLevel.entries.toSet(),
    val packageName: String = "",
    val tag: String = "",
    val event: String = "",
    val sessionId: String = "",
    val keyword: String = "",
    val timeRange: LogTimeRange = LogTimeRange.ALL,
) {
    val isDefault: Boolean
        get() = this == LogFilterState()

    fun toQuery(now: Long = System.currentTimeMillis()): LogQuery {
        return LogQuery(
            source = source,
            levels = levels,
            packageName = packageName,
            tag = tag,
            event = event,
            sessionId = sessionId,
            keyword = keyword,
            fromMillis = timeRange.fromMillis(now),
        )
    }
}

enum class LogTimeRange(private val durationMillis: Long?) {
    ALL(null),
    DAY(24L * 60L * 60L * 1_000L),
    WEEK(7L * 24L * 60L * 60L * 1_000L),
    MONTH(30L * 24L * 60L * 60L * 1_000L);

    fun fromMillis(now: Long): Long? = durationMillis?.let(now::minus)
}
