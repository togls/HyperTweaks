package io.github.togls.hypertweaks.core.xposed.util

import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.Logger

class HookLog private constructor(
    private val delegate: Logger,
) {

    fun child(component: String): HookLog {
        return HookLog(delegate.child(component))
    }

    fun d(message: String, vararg fields: Pair<String, Any?>) = delegate.d(message, *fields)

    fun i(
        message: String,
        vararg fields: Pair<String, Any?>,
    ) {
        delegate.i(message, *fields)
    }

    fun w(
        message: String,
        error: Throwable? = null,
        vararg fields: Pair<String, Any?>,
    ) {
        delegate.w(message, error, *fields)
    }

    fun e(
        message: String,
        error: Throwable? = null,
        vararg fields: Pair<String, Any?>,
    ) {
        delegate.e(message, error, *fields)
    }

    fun event(
        level: LogLevel,
        event: String,
        message: String,
        error: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    ) {
        when (level) {
            LogLevel.DEBUG -> delegate.debug(event, message, error, fields)
            LogLevel.INFO -> delegate.info(event, message, error, fields)
            LogLevel.WARN -> delegate.warn(event, message, error, fields)
            LogLevel.ERROR -> delegate.error(event, message, error, fields)
        }
    }

    companion object {
        fun create(logger: Logger): HookLog = HookLog(logger)
    }
}
