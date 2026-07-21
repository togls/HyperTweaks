package io.github.togls.hypertweaks.logging.api

object LogLimits {
    const val MaxMessageChars = 64 * 1024
    const val MaxStackTraceChars = 256 * 1024
    const val MaxFieldCount = 64
    const val HookBufferEventCount = 256
    const val AppBufferEventCount = 256
    const val IpcBatchEventCount = 32
    const val IpcTargetBatchBytes = 256 * 1024
    const val IpcFlushDelayMillis = 1_000L
    const val AppWriteBatchEventCount = 64
    const val AppWriteDelayMillis = 250L
    const val MaxDatabaseRows = 100_000
    const val MaxRetentionDays = 30L
}
