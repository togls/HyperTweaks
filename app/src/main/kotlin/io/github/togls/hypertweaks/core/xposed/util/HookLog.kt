package io.github.togls.hypertweaks.core.xposed.util

import android.util.Log
import io.github.libxposed.api.XposedModule

class HookLog private constructor(
    private val module: XposedModule,
    private val component: String?,
) {

    fun child(component: String): HookLog {
        val nextComponent = this.component
            ?.let { parent -> "$parent/$component" }
            ?: component

        return HookLog(
            module = module,
            component = nextComponent,
        )
    }

    fun i(
        message: String,
        vararg fields: Pair<String, Any?>,
    ) {
        log(
            priority = Log.INFO,
            message = message,
            fields = fields,
        )
    }

    fun w(
        message: String,
        error: Throwable? = null,
        vararg fields: Pair<String, Any?>,
    ) {
        log(
            priority = Log.WARN,
            message = message,
            error = error,
            fields = fields,
        )
    }

    fun e(
        message: String,
        error: Throwable? = null,
        vararg fields: Pair<String, Any?>,
    ) {
        log(
            priority = Log.ERROR,
            message = message,
            error = error,
            fields = fields,
        )
    }

    private fun log(
        priority: Int,
        message: String,
        error: Throwable? = null,
        fields: Array<out Pair<String, Any?>>,
    ) {
        val formattedMessage = buildString {
            if (!component.isNullOrBlank()) {
                append('[')
                append(component)
                append("] ")
            }

            append(message)

            fields.forEach { (key, value) ->
                append(' ')
                append(key)
                append('=')
                append(formatValue(value))
            }
        }

        if (error == null) {
            module.log(priority, TAG, formattedMessage)
        } else {
            module.log(priority, TAG, formattedMessage, error)
        }
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> value
            is Enum<*> -> value.name
            else -> value.toString()
        }
    }

    companion object {
        private const val TAG = "HyperTweaks"

        fun create(module: XposedModule): HookLog {
            return HookLog(
                module = module,
                component = null,
            )
        }
    }
}