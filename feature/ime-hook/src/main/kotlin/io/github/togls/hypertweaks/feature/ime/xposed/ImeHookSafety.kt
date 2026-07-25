package io.github.togls.hypertweaks.feature.ime.xposed

import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import io.github.togls.hypertweaks.logging.api.Logger

internal inline fun <T> preserveOriginalOnFailure(
    log: Logger,
    event: String,
    originalValue: T,
    action: () -> T,
): T {
    val fields = mapOf("subtarget" to event, "reason" to "callback_invoked")
    log.debug(event = "hook.callback.entered", fields = fields)
    return try {
        action().also {
            log.debug(
                event = "hook.callback.transformed",
                fields = fields + ("reason" to "callback_completed"),
            )
        }
    } catch (error: Throwable) {
        if (error is Error) throw error
        log.error(
            event = "hook.callback.failed",
            message = event,
            throwable = error,
            fields = fields + ("reason" to "callback_exception"),
        )
        originalValue
    }
}

internal inline fun installImeTarget(
    target: String,
    log: Logger,
    action: () -> Unit,
): ImeTargetInstallResult {
    val fields = mapOf("subtarget" to target, "reason" to "target_members_resolved")
    log.debug(
        event = "target.resolve.succeeded",
        fields = fields,
    )
    return try {
        action()
        ImeTargetInstallResult.Installed(target)
    } catch (error: Throwable) {
        if (error is Error) throw error
        log.error(
            event = "hook.install.failed",
            throwable = error,
            fields = mapOf("subtarget" to target, "reason" to "installer_exception"),
        )
        ImeTargetInstallResult.Failed(target, error)
    }
}

internal fun skipImeTarget(
    target: String,
    reason: String,
    log: Logger,
): ImeTargetInstallResult {
    val fields = mapOf("subtarget" to target, "reason" to reason)
    log.warn(event = "target.resolve.failed", message = reason, fields = fields)
    return ImeTargetInstallResult.Skipped(target, reason)
}

internal fun logImeTargetResolveStarted(
    log: Logger,
    target: String,
) {
    log.debug(
        event = "target.resolve.started",
        fields = mapOf("subtarget" to target, "reason" to "resolution_started"),
    )
}

internal fun logImeCallbackBypassed(
    log: Logger,
    subtarget: String,
    reason: String,
) {
    val fields = mapOf("subtarget" to subtarget, "reason" to reason)
    log.debug(event = "hook.callback.entered", fields = fields)
    log.debug(event = "hook.callback.bypassed", fields = fields)
}
