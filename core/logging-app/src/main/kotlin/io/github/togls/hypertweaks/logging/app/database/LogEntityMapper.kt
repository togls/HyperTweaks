package io.github.togls.hypertweaks.logging.app.database

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource

object LogEntityMapper {
    fun toEntity(event: LogEvent, createdAt: Long = System.currentTimeMillis()): LogEntryEntity {
        return LogEntryEntity(
            eventId = event.eventId,
            timestampMillis = event.timestampMillis,
            elapsedRealtime = event.elapsedRealtimeMillis,
            source = event.source.name,
            level = event.level.name,
            tag = event.tag,
            event = event.event,
            message = event.message,
            packageName = event.packageName,
            processName = event.processName,
            pid = event.pid,
            tid = event.tid,
            sessionId = event.sessionId,
            fieldsJson = FlatJsonObjectCodec.encode(event.fields),
            throwableType = event.throwableType,
            throwableMessage = event.throwableMessage,
            stackTrace = event.stackTrace,
            createdAt = createdAt,
        )
    }

    fun toEvent(entity: LogEntryEntity): LogEvent {
        return LogEvent(
            eventId = entity.eventId,
            timestampMillis = entity.timestampMillis,
            elapsedRealtimeMillis = entity.elapsedRealtime,
            source = LogSource.valueOf(entity.source),
            level = LogLevel.valueOf(entity.level),
            tag = entity.tag,
            event = entity.event,
            message = entity.message,
            packageName = entity.packageName,
            processName = entity.processName,
            pid = entity.pid,
            tid = entity.tid,
            sessionId = entity.sessionId,
            fields = FlatJsonObjectCodec.decode(entity.fieldsJson),
            throwableType = entity.throwableType,
            throwableMessage = entity.throwableMessage,
            stackTrace = entity.stackTrace,
        )
    }
}

internal object FlatJsonObjectCodec {
    fun encode(fields: Map<String, String>): String {
        return fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }
    }

    fun decode(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return runCatching { Parser(json).parse() }.getOrDefault(emptyMap())
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Map<String, String> {
            val result = linkedMapOf<String, String>()
            expect('{')
            skipWhitespace()
            while (peek() != '}') {
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = readString()
                skipWhitespace()
                if (peek() == ',') {
                    index++
                    skipWhitespace()
                } else {
                    break
                }
            }
            expect('}')
            return result.toMap()
        }

        private fun readString(): String {
            expect('"')
            return buildString {
                while (index < text.length) {
                    val character = text[index++]
                    when {
                        character == '"' -> return@buildString
                        character == '\\' -> append(readEscaped())
                        else -> append(character)
                    }
                }
                error("Unterminated JSON string")
            }
        }

        private fun readEscaped(): Char {
            val escaped = text.getOrNull(index++) ?: error("Invalid JSON escape")
            return when (escaped) {
                '"' -> '"'
                '\\' -> '\\'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> error("Unsupported JSON escape")
            }
        }

        private fun expect(expected: Char) {
            check(peek() == expected) { "Expected $expected at $index" }
            index++
        }

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) index++
        }

        private fun peek(): Char? = text.getOrNull(index)
    }
}
