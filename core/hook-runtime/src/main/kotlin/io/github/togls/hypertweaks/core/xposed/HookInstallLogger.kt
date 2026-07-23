package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger

internal class HookInstallLogger(
    private val logger: Logger,
) {
    fun loggerFor(featureId: String): Logger = logger.child(featureId)

    fun registryStarted(environment: HookEnvironment) {
        logger.info(
            event = "hook.registry.started",
            fields = environmentFields(environment),
        )
    }

    fun matchStarted(feature: HookFeature, environment: HookEnvironment) {
        logger.debug(
            event = "feature.match.started",
            fields = featureFields(feature, environment),
        )
    }

    fun matchSucceeded(feature: HookFeature, environment: HookEnvironment) {
        logger.debug(
            event = "feature.match.succeeded",
            fields = featureFields(feature, environment),
        )
    }

    fun disabled(feature: HookFeature, environment: HookEnvironment) {
        logger.info(
            event = "feature.config.disabled",
            fields = featureFields(feature, environment),
        )
    }

    fun duplicate(feature: HookFeature, environment: HookEnvironment) {
        logger.info(
            event = "hook.install.skipped",
            fields = featureFields(feature, environment) + ("reason" to "already_attempted"),
        )
    }

    fun installStarted(feature: HookFeature, environment: HookEnvironment) {
        logger.info(
            event = "hook.install.started",
            fields = featureFields(feature, environment),
        )
    }

    fun installed(
        feature: HookFeature,
        environment: HookEnvironment,
        result: HookInstallResult.Installed,
    ) {
        logger.info(
            event = "hook.install.succeeded",
            fields = featureFields(feature, environment) + mapOf(
                "installed_targets" to result.installedTargets.sorted().joinToString(),
                "failed_targets" to result.failedTargets.sorted().joinToString(),
            ),
        )
    }

    fun unsupported(
        feature: HookFeature,
        environment: HookEnvironment,
        reason: String,
    ) {
        logger.info(
            event = "hook.install.skipped",
            fields = featureFields(feature, environment) + mapOf(
                "reason" to "unsupported_target",
                "detail" to reason,
            ),
        )
    }

    fun failed(
        feature: HookFeature,
        environment: HookEnvironment,
        error: Throwable,
    ) {
        logger.error(
            event = "hook.install.failed",
            throwable = error,
            fields = featureFields(feature, environment),
        )
    }

    fun settingsUnavailable(environment: HookEnvironment, error: Throwable?) {
        logger.warn(
            event = "config.snapshot.unavailable",
            message = "Hook features are disabled because remote preferences are unavailable",
            throwable = error,
            fields = environmentFields(environment),
        )
    }

    private fun featureFields(
        feature: HookFeature,
        environment: HookEnvironment,
    ): Map<String, String> {
        return environmentFields(environment) + ("feature" to feature.id)
    }

    private fun environmentFields(environment: HookEnvironment): Map<String, String> {
        return mapOf(
            "target" to environment.packageName,
            "process" to environment.processName,
            "session_id" to environment.sessionId,
        )
    }
}
