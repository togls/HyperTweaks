package io.github.togls.hypertweaks.logging.api

data class LogEvent(
    val eventId: String,
    val timestampMillis: Long,
    val elapsedRealtimeMillis: Long?,
    val source: LogSource,
    val level: LogLevel,
    val tag: String,
    val event: String,
    val message: String?,
    val packageName: String?,
    val processName: String?,
    val pid: Int?,
    val tid: Int?,
    val sessionId: String?,
    val fields: Map<String, String>,
    val throwableType: String?,
    val throwableMessage: String?,
    val stackTrace: String?,
) {
    companion object {
        private val EventPattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}$")

        fun isValidEventName(value: String): Boolean = EventPattern.matches(value)
    }
}
