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
    INSTALLING,
    INSTALLED,
    FAILED,
}

interface HookInstallGuard {
    fun tryStart(key: HookInstallKey): Boolean

    fun markInstalled(key: HookInstallKey)

    fun markFailed(key: HookInstallKey)

    fun state(key: HookInstallKey): HookInstallState
}
