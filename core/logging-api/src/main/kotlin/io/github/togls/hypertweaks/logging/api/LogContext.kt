package io.github.togls.hypertweaks.logging.api

data class LogContext(
    val tag: String = "",
    val packageName: String? = null,
    val processName: String? = null,
    val pid: Int? = null,
    val tid: Int? = null,
    val sessionId: String? = null,
    val fields: Map<String, String> = emptyMap(),
) {
    fun child(childTag: String): LogContext {
        val nextTag = listOf(tag, childTag)
            .filter(String::isNotBlank)
            .joinToString("/")
        return copy(tag = nextTag)
    }

    fun withFields(additionalFields: Map<String, String>): LogContext {
        return copy(fields = (fields + additionalFields).toMap())
    }

    fun overlay(overlay: LogContext): LogContext {
        return copy(
            tag = overlay.tag.ifBlank { tag },
            packageName = overlay.packageName ?: packageName,
            processName = overlay.processName ?: processName,
            pid = overlay.pid ?: pid,
            tid = overlay.tid ?: tid,
            sessionId = overlay.sessionId ?: sessionId,
            fields = (fields + overlay.fields).toMap(),
        )
    }
}
