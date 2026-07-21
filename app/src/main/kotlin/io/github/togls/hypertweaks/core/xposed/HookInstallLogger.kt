package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger

class HookInstallLogger(
    private val log: Logger,
) {

    fun startSystemServer(target: String) {
        log.info(
            event = "hook.registry.started",
            fields = mapOf("target" to target),
        )
    }

    fun startPackage(packageName: String) {
        log.info(
            event = "hook.registry.started",
            fields = mapOf("target" to packageName),
        )
    }

    fun installed(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        log.info(
            event = "hook.install.succeeded",
            fields = fields(name, target, feature),
        )
    }

    fun skippedDisabled(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        log.info(
            event = "hook.install.skipped",
            fields = fields(name, target, feature) + ("reason" to "feature_disabled"),
        )
    }

    fun skippedUnsupported(
        name: String,
        target: String,
        feature: HookFeature,
    ) {
        log.info(
            event = "hook.install.skipped",
            fields = fields(name, target, feature) + ("reason" to "unsupported_target"),
        )
    }

    fun failed(
        name: String,
        target: String,
        feature: HookFeature,
        error: Throwable,
    ) {
        log.error(
            event = "hook.install.failed",
            throwable = error,
            fields = fields(name, target, feature),
        )
    }

    private fun fields(
        name: String,
        target: String,
        feature: HookFeature,
    ): Map<String, String> {
        return mapOf(
            "name" to name,
            "target" to target,
            "feature" to feature.name,
        )
    }
}
