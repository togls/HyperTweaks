package io.github.togls.hypertweaks.logging.api

import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

class LogEventBuilder(
    private val source: LogSource,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val elapsedClock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val eventIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun build(
        context: LogContext,
        level: LogLevel,
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ): LogEvent {
        val safeEvent = event.takeIf(LogEvent::isValidEventName) ?: "logging.event.invalid"
        return LogEvent(
            eventId = eventIdFactory(),
            timestampMillis = wallClock(),
            elapsedRealtimeMillis = elapsedClock(),
            source = source,
            level = level,
            tag = context.tag,
            event = safeEvent,
            message = message?.take(LogLimits.MaxMessageChars),
            packageName = context.packageName,
            processName = context.processName,
            pid = context.pid,
            tid = context.tid,
            sessionId = context.sessionId,
            fields = boundedFields(context.fields + fields),
            throwableType = throwable?.javaClass?.name,
            throwableMessage = throwable?.message?.take(LogLimits.MaxMessageChars),
            stackTrace = throwable?.toStackTraceText()?.take(LogLimits.MaxStackTraceChars),
        )
    }

    private fun boundedFields(fields: Map<String, String>): Map<String, String> {
        return fields.entries
            .take(LogLimits.MaxFieldCount)
            .associateTo(linkedMapOf()) { entry ->
                entry.key.take(LogLimits.MaxMessageChars) to entry.value.take(LogLimits.MaxMessageChars)
            }
            .toMap()
    }

    private fun Throwable.toStackTraceText(): String {
        return StringWriter().use { writer ->
            PrintWriter(writer).use(::printStackTrace)
            writer.toString()
        }
    }
}
