package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger

class CallbackGuard(
    private val logger: Logger,
) {
    fun <T> protect(
        operation: String,
        fallback: () -> T,
        callback: () -> T,
    ): T {
        return try {
            callback()
        } catch (error: Exception) {
            logger.error(
                event = "hook.callback.failed",
                throwable = error,
                fields = mapOf("operation" to operation),
            )
            fallback()
        }
    }
}
