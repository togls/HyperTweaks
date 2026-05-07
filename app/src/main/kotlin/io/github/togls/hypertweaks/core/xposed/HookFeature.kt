package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys

enum class HookFeature(
    val preferenceKey: String,
) {
    Ime(RemotePreferenceKeys.ImeEnabled),
    KeepAlive(RemotePreferenceKeys.KeepAliveEnabled),
}