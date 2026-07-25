package io.github.togls.hypertweaks.core.xposed

data class HookInstallKey(
    val featureId: String,
    val packageName: String,
    val processName: String,
    val classLoaderIdentity: Int,
    val targetId: String,
)

enum class HookInstallState {
    NEW,
    WAITING_FOR_SETTINGS,
    INSTALLING,
    INSTALLED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
}

interface HookInstallGuard {
    fun tryStart(key: HookInstallKey): Boolean

    fun markInstalled(key: HookInstallKey)

    fun markDeferred(key: HookInstallKey)

    /**
     * 记录安装失败，并在允许重试时返回下一次调度所需的延迟。
     *
     * 返回 null 表示失败不可重试，或有限重试次数已经耗尽。
     */
    fun markFailed(
        key: HookInstallKey,
        retryable: Boolean,
        failureStage: String,
        failureMessage: String?,
    ): Long?

    fun state(key: HookInstallKey): HookInstallState

    fun record(key: HookInstallKey): HookInstallRecord
}

data class HookInstallRecord(
    val state: HookInstallState = HookInstallState.NEW,
    val attemptCount: Int = 0,
    val retryCount: Int = 0,
    val lastFailureAtMillis: Long? = null,
    val nextRetryAtMillis: Long? = null,
    val lastFailureStage: String? = null,
    val lastFailureMessage: String? = null,
)
