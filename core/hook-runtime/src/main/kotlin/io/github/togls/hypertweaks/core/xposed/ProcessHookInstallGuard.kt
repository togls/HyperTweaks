package io.github.togls.hypertweaks.core.xposed

import java.util.concurrent.ConcurrentHashMap

class ProcessHookInstallGuard(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val retryBackoffMillis: LongArray = DefaultRetryBackoffMillis,
) : HookInstallGuard {
    private val records = ConcurrentHashMap<HookInstallKey, HookInstallRecord>()

    override fun tryStart(key: HookInstallKey): Boolean {
        val now = nowMillis()
        var started = false
        records.compute(key) { _, currentRecord ->
            val current = currentRecord ?: HookInstallRecord()
            val canRetry = current.state == HookInstallState.FAILED_RETRYABLE &&
                now >= (current.nextRetryAtMillis ?: Long.MAX_VALUE)
            val waitingForSettings = current.state == HookInstallState.WAITING_FOR_SETTINGS
            if (current.state != HookInstallState.NEW && !canRetry && !waitingForSettings) {
                return@compute current
            }
            started = true
            current.copy(
                state = HookInstallState.INSTALLING,
                attemptCount = current.attemptCount + 1,
                nextRetryAtMillis = null,
            )
        }
        return started
    }

    override fun markInstalled(key: HookInstallKey) {
        records.computeIfPresent(key) { _, record ->
            require(record.state == HookInstallState.INSTALLING) {
                "Cannot mark hook installed from state ${record.state}"
            }
            record.copy(state = HookInstallState.INSTALLED)
        }
    }

    override fun markDeferred(key: HookInstallKey) {
        records.computeIfPresent(key) { _, record ->
            require(record.state == HookInstallState.INSTALLING) {
                "Cannot defer hook installation from state ${record.state}"
            }
            record.copy(state = HookInstallState.WAITING_FOR_SETTINGS)
        }
    }

    override fun markFailed(
        key: HookInstallKey,
        retryable: Boolean,
        failureStage: String,
        failureMessage: String?,
    ): Long? {
        val now = nowMillis()
        var retryDelayMillis: Long? = null
        records.computeIfPresent(key) { _, record ->
            require(record.state == HookInstallState.INSTALLING) {
                "Cannot mark hook failed from state ${record.state}"
            }
            val backoffIndex = record.attemptCount - 1
            val canRetry = retryable && backoffIndex in retryBackoffMillis.indices
            retryDelayMillis = retryBackoffMillis.getOrNull(backoffIndex)
                ?.takeIf { canRetry }
            record.copy(
                state = if (canRetry) {
                    HookInstallState.FAILED_RETRYABLE
                } else {
                    HookInstallState.FAILED_TERMINAL
                },
                retryCount = (record.attemptCount - 1).coerceAtLeast(0),
                lastFailureAtMillis = now,
                nextRetryAtMillis = retryDelayMillis?.let(now::plus),
                lastFailureStage = failureStage,
                lastFailureMessage = failureMessage,
            )
        }
        return retryDelayMillis
    }

    override fun state(key: HookInstallKey): HookInstallState {
        return record(key).state
    }

    override fun record(key: HookInstallKey): HookInstallRecord {
        return records[key] ?: HookInstallRecord()
    }

    private companion object {
        val DefaultRetryBackoffMillis = longArrayOf(100L, 500L, 2_000L)
    }
}
