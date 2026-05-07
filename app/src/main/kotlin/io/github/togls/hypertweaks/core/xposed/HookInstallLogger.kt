package io.github.togls.hypertweaks.core.xposed

import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.core.xposed.util.HookLog

class HookInstallLogger(
    private val log: HookLog,
) {

    fun startSystemServer(target: String) {
        log.i(
            message = "install_start",
            "target" to target
        )
    }

    fun startPackage(packageName: String) {
        log.i(
            message = "install_start",
            "target" to packageName,
        )
    }

    fun installed(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        log.i(
            message = "hook_installed",
            "name" to name,
            "target" to target,
            "feature" to feature.name,
        )
    }

    fun skippedDisabled(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        log.i(
            message = "hook_skipped",
            "name" to name,
            "target" to target,
            "feature" to feature.name,
            "reason" to "feature_disabled",
        )
    }

    fun skippedUnsupported(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        log.i(
            message = "hook_skipped",
            "name" to name,
            "target" to target,
            "feature" to feature.name,
            "reason" to "unsupported_target",
        )
    }

    fun failed(
        name: String,
        target: String,
        feature: HookFeature,
        error: Throwable,
    ) {
        log.i(
            message = "hook_failed",
            "name" to name,
            "target" to target,
            "feature" to feature.name,
            "error" to error.message,
        )
    }
}