package io.github.togls.hypertweaks.logging.api

object LogTextFormatter {
    fun format(event: LogEvent): String {
        return buildString {
            if (event.tag.isNotBlank()) append("[${event.tag}] ")
            append(event.event)
            event.message?.let { message -> append(" - ").append(message) }
            event.fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
            appendThrowable(event)
        }
    }

    private fun StringBuilder.appendThrowable(event: LogEvent) {
        event.throwableType?.let { type ->
            append(" throwable=").append(type)
            event.throwableMessage?.let { message -> append(": ").append(message) }
        }
        event.stackTrace?.let { stack -> append('\n').append(stack) }
    }
}
