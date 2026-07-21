package io.github.togls.hypertweaks.logging.api

import android.os.Bundle

object LogProtocol {
    const val Version1 = 1
    const val MethodAppendLogs = "append_logs_v1"
    const val Authority = "io.github.togls.hypertweaks.log.ingest"

    fun encodeBatch(envelope: LogBatchEnvelope): Bundle {
        return Bundle().apply {
            putInt(KeyProtocolVersion, Version1)
            putString(KeyBatchId, envelope.batchId)
            putString(KeySenderPackage, envelope.senderPackage)
            putString(KeySenderProcess, envelope.senderProcess)
            putInt(KeySenderPid, envelope.senderPid)
            putParcelableArrayList(KeyEvents, ArrayList(envelope.events.map(::encodeEvent)))
        }
    }

    fun decodeBatch(bundle: Bundle): Result<LogBatchEnvelope> = runCatching {
        require(bundle.getInt(KeyProtocolVersion, -1) == Version1) { "Unsupported protocol version" }
        val events = bundle.getParcelableArrayList(KeyEvents, Bundle::class.java)
            ?.map(::decodeEvent)
            ?: error("Missing events")
        require(events.size <= LogLimits.IpcBatchEventCount) { "Batch contains too many events" }
        require(estimateBytes(events) <= LogLimits.IpcTargetBatchBytes) { "Batch exceeds size limit" }
        LogBatchEnvelope(
            batchId = bundle.requireString(KeyBatchId),
            senderPackage = bundle.requireString(KeySenderPackage),
            senderProcess = bundle.getString(KeySenderProcess),
            senderPid = bundle.getInt(KeySenderPid),
            events = events,
        )
    }

    fun encodeResult(result: LogIngestResult): Bundle {
        return Bundle().apply {
            putInt(KeyAcceptedCount, result.acceptedCount)
            putInt(KeyRejectedCount, result.rejectedCount)
            putBoolean(KeyRetryable, result.retryable)
            putString(KeyErrorCode, result.errorCode)
        }
    }

    fun decodeResult(bundle: Bundle?): LogIngestResult {
        if (bundle == null) return LogIngestResult(0, 0, true, "empty_response")
        return LogIngestResult(
            acceptedCount = bundle.getInt(KeyAcceptedCount),
            rejectedCount = bundle.getInt(KeyRejectedCount),
            retryable = bundle.getBoolean(KeyRetryable),
            errorCode = bundle.getString(KeyErrorCode),
        )
    }

    fun estimateBytes(events: List<LogEvent>): Int {
        return events.sumOf { event ->
            listOfNotNull(
                event.eventId, event.tag, event.event, event.message, event.packageName,
                event.processName, event.sessionId, event.throwableType, event.throwableMessage,
                event.stackTrace,
            ).sumOf(String::length) * 2 +
                event.fields.entries.sumOf { (key, value) -> (key.length + value.length) * 2 } + 128
        }
    }

    private fun encodeEvent(event: LogEvent): Bundle {
        return Bundle().apply {
            putString(KeyEventId, event.eventId)
            putLong(KeyTimestamp, event.timestampMillis)
            event.elapsedRealtimeMillis?.let { putLong(KeyElapsed, it) }
            putString(KeySource, event.source.name)
            putString(KeyLevel, event.level.name)
            putString(KeyTag, event.tag)
            putString(KeyEvent, event.event)
            putString(KeyMessage, event.message)
            putString(KeyPackage, event.packageName)
            putString(KeyProcess, event.processName)
            event.pid?.let { putInt(KeyPid, it) }
            event.tid?.let { putInt(KeyTid, it) }
            putString(KeySession, event.sessionId)
            putBundle(KeyFields, Bundle().apply { event.fields.forEach(::putString) })
            putString(KeyThrowableType, event.throwableType)
            putString(KeyThrowableMessage, event.throwableMessage)
            putString(KeyStackTrace, event.stackTrace)
        }
    }

    private fun decodeEvent(bundle: Bundle): LogEvent {
        val fieldsBundle = bundle.getBundle(KeyFields) ?: Bundle.EMPTY
        val fields = fieldsBundle.keySet().associateWith { key -> fieldsBundle.requireString(key) }
        return LogEvent(
            eventId = bundle.requireString(KeyEventId),
            timestampMillis = bundle.getLong(KeyTimestamp),
            elapsedRealtimeMillis = bundle.optionalLong(KeyElapsed),
            source = enumValueOf(bundle.requireString(KeySource)),
            level = enumValueOf(bundle.requireString(KeyLevel)),
            tag = bundle.requireString(KeyTag),
            event = bundle.requireString(KeyEvent),
            message = bundle.getString(KeyMessage),
            packageName = bundle.getString(KeyPackage),
            processName = bundle.getString(KeyProcess),
            pid = bundle.optionalInt(KeyPid),
            tid = bundle.optionalInt(KeyTid),
            sessionId = bundle.getString(KeySession),
            fields = fields,
            throwableType = bundle.getString(KeyThrowableType),
            throwableMessage = bundle.getString(KeyThrowableMessage),
            stackTrace = bundle.getString(KeyStackTrace),
        )
    }

    private fun Bundle.requireString(key: String): String = getString(key) ?: error("Missing $key")
    private fun Bundle.optionalLong(key: String): Long? = if (containsKey(key)) getLong(key) else null
    private fun Bundle.optionalInt(key: String): Int? = if (containsKey(key)) getInt(key) else null

    private const val KeyProtocolVersion = "protocol_version"
    private const val KeyBatchId = "batch_id"
    private const val KeySenderPackage = "sender_package"
    private const val KeySenderProcess = "sender_process"
    private const val KeySenderPid = "sender_pid"
    private const val KeyEvents = "events"
    private const val KeyAcceptedCount = "accepted_count"
    private const val KeyRejectedCount = "rejected_count"
    private const val KeyRetryable = "retryable"
    private const val KeyErrorCode = "error_code"
    private const val KeyEventId = "event_id"
    private const val KeyTimestamp = "timestamp_millis"
    private const val KeyElapsed = "elapsed_realtime"
    private const val KeySource = "source"
    private const val KeyLevel = "level"
    private const val KeyTag = "tag"
    private const val KeyEvent = "event"
    private const val KeyMessage = "message"
    private const val KeyPackage = "package_name"
    private const val KeyProcess = "process_name"
    private const val KeyPid = "pid"
    private const val KeyTid = "tid"
    private const val KeySession = "session_id"
    private const val KeyFields = "fields"
    private const val KeyThrowableType = "throwable_type"
    private const val KeyThrowableMessage = "throwable_message"
    private const val KeyStackTrace = "stack_trace"
}

data class LogBatchEnvelope(
    val batchId: String,
    val senderPackage: String,
    val senderProcess: String?,
    val senderPid: Int,
    val events: List<LogEvent>,
)

data class LogIngestResult(
    val acceptedCount: Int,
    val rejectedCount: Int,
    val retryable: Boolean,
    val errorCode: String?,
) {
    val succeeded: Boolean
        get() = !retryable && rejectedCount == 0
}
