package io.github.togls.hypertweaks.core.xposed

data class HookSettingsSnapshot(
    val enabledPreferenceKeys: Set<String> = emptySet(),
    val navBarLayoutStart: String = DefaultNavBarStart,
    val navBarLayoutEnd: String = DefaultNavBarEnd,
    val navBarLayoutHandle: String = "",
    val keepAliveMode: String = DefaultKeepAliveMode,
    val keepAlivePackages: String = "",
) {
    fun isEnabled(preferenceKey: String): Boolean = preferenceKey in enabledPreferenceKeys

    companion object {
        const val DefaultNavBarStart = "back"
        const val DefaultNavBarEnd = "ime_switcher"
        const val DefaultKeepAliveMode = "oom_only"

        val Disabled = HookSettingsSnapshot()
    }
}

sealed interface HookSettingsState {
    data class Ready(
        val snapshot: HookSettingsSnapshot,
    ) : HookSettingsState

    data class Unavailable(
        val reason: Throwable?,
    ) : HookSettingsState
}

interface HookSettingsProvider {
    val currentState: HookSettingsState

    fun subscribe(listener: (HookSettingsState) -> Unit): HookSettingsSubscription
}

fun interface HookSettingsSubscription : AutoCloseable {
    override fun close()
}

fun HookSettingsState.snapshotOrDisabled(): HookSettingsSnapshot {
    return (this as? HookSettingsState.Ready)?.snapshot ?: HookSettingsSnapshot.Disabled
}
