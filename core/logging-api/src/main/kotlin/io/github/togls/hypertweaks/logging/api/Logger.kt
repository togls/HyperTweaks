package io.github.togls.hypertweaks.logging.api

interface Logger {
    fun child(tag: String): Logger

    fun withField(key: String, value: Any?): Logger

    fun withFields(fields: Map<String, String>): Logger

    fun withContext(context: LogContext): Logger

    fun debug(
        event: String,
        message: String? = null,
        throwable: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    )

    fun info(
        event: String,
        message: String? = null,
        throwable: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    )

    fun warn(
        event: String,
        message: String? = null,
        throwable: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    )

    fun error(
        event: String,
        message: String? = null,
        throwable: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    )

    fun d(message: String, vararg fields: Pair<String, Any?>) {
        debug("legacy.message.debug", message, fields = fields.toLogFields())
    }

    fun i(message: String, vararg fields: Pair<String, Any?>) {
        info("legacy.message.info", message, fields = fields.toLogFields())
    }

    fun w(message: String, error: Throwable? = null, vararg fields: Pair<String, Any?>) {
        warn("legacy.message.warn", message, error, fields.toLogFields())
    }

    fun e(message: String, error: Throwable? = null, vararg fields: Pair<String, Any?>) {
        error("legacy.message.error", message, error, fields.toLogFields())
    }
}

object NoOpLogger : Logger {
    override fun child(tag: String): Logger = this
    override fun withField(key: String, value: Any?): Logger = this
    override fun withFields(fields: Map<String, String>): Logger = this
    override fun withContext(context: LogContext): Logger = this
    override fun debug(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) = Unit
    override fun info(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) = Unit
    override fun warn(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) = Unit
    override fun error(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) = Unit
}

object LoggerFactory {
    fun create(
        source: LogSource,
        modeProvider: () -> LogMode,
        sink: LogSink,
        context: LogContext = LogContext(),
        builder: LogEventBuilder = LogEventBuilder(source),
    ): Logger {
        return EventLogger(modeProvider, sink, context, builder)
    }
}

private class EventLogger(
    private val modeProvider: () -> LogMode,
    private val sink: LogSink,
    private val context: LogContext,
    private val builder: LogEventBuilder,
) : Logger {
    override fun child(tag: String): Logger = copy(context.child(tag))

    override fun withField(key: String, value: Any?): Logger {
        return withFields(mapOf(key to value.toLogValue()))
    }

    override fun withFields(fields: Map<String, String>): Logger = copy(context.withFields(fields))

    override fun withContext(context: LogContext): Logger = copy(this.context.overlay(context))

    override fun debug(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) {
        emit(LogLevel.DEBUG, event, message, throwable, fields)
    }

    override fun info(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) {
        emit(LogLevel.INFO, event, message, throwable, fields)
    }

    override fun warn(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) {
        emit(LogLevel.WARN, event, message, throwable, fields)
    }

    override fun error(event: String, message: String?, throwable: Throwable?, fields: Map<String, String>) {
        emit(LogLevel.ERROR, event, message, throwable, fields)
    }

    private fun emit(
        level: LogLevel,
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) {
        val mode = modeProvider()
        if (!LogPolicy.allows(mode, level)) return
        runCatching {
            val eventFields = fields + ("log_mode" to mode.persistedValue)
            sink.emit(builder.build(context, level, event, message, throwable, eventFields))
        }
    }

    private fun copy(context: LogContext): Logger {
        return EventLogger(modeProvider, sink, context, builder)
    }
}

private fun Array<out Pair<String, Any?>>.toLogFields(): Map<String, String> {
    return associate { (key, value) -> key to value.toLogValue() }
}

private fun Any?.toLogValue(): String {
    return when (this) {
        null -> "null"
        is Enum<*> -> name
        else -> toString()
    }
}
