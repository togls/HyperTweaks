package io.github.togls.hypertweaks.core.xposed

import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.core.xposed.util.HookLog

class HookInstallLogger(
    private val module: XposedModule,
) {

    fun startSystemServer() {
        HookLog.i(
            module,
            "hook install start: process=system_server",
        )
    }

    fun startPackage(packageName: String) {
        HookLog.i(
            module,
            "hook install start: package=$packageName",
        )
    }

    fun installed(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        HookLog.i(
            module,
            "hook installed: name=$name target=$target feature=$feature",
        )
    }

    fun skippedDisabled(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        HookLog.i(
            module,
            "hook skipped: name=$name target=$target feature=$feature reason=feature_disabled",
        )
    }

    fun skippedUnsupported(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        HookLog.i(
            module,
            "hook skipped: name=$name target=$target feature=$feature reason=unsupported_target",
        )
    }

    fun failed(
        name: String,
        target: String,
        feature: HookFeature,
        error: Throwable,
    ) {
        HookLog.e(
            module,
            "hook failed: name=$name target=$target feature=$feature",
            error,
        )
    }
}