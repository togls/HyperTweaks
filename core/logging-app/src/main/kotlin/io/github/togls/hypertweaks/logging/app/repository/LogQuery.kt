package io.github.togls.hypertweaks.logging.app.repository

import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource

data class LogQuery(
    val source: LogSource? = null,
    val levels: Set<LogLevel> = LogLevel.entries.toSet(),
    val packageName: String? = null,
    val tag: String? = null,
    val event: String? = null,
    val sessionId: String? = null,
    val keyword: String? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
) {
    fun normalized(): LogQuery {
        return copy(
            packageName = packageName.nullIfBlank(),
            tag = tag.nullIfBlank(),
            event = event.nullIfBlank(),
            sessionId = sessionId.nullIfBlank(),
            keyword = keyword.nullIfBlank(),
        )
    }

    private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
