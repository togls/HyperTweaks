package io.github.togls.hypertweaks.service

import io.github.libxposed.service.XposedService
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys

internal object HookLogRecoveryRequester {
    fun request(service: XposedService): Result<Long> = runCatching {
        check(service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L) {
            "The current Xposed framework does not support remote preferences"
        }
        val preferences = service.getRemotePreferences(RemotePreferenceKeys.GroupName)
        val nextGeneration = preferences.getLong(
            RemotePreferenceKeys.LogBridgeRecoveryGeneration,
            0L,
        ) + 1L
        val committed = preferences.edit()
            .putLong(RemotePreferenceKeys.LogBridgeRecoveryGeneration, nextGeneration)
            .commit()
        check(committed) { "Failed to request Hook log recovery" }
        nextGeneration
    }
}
