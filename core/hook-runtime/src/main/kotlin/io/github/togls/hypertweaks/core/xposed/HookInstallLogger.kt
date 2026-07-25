package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger

internal class HookInstallLogger(
    private val logger: Logger,
) {
    fun loggerFor(
        featureId: String,
        configVersion: Long,
    ): Logger {
        return logger.child(featureId).withFields(
            mapOf(
                "feature" to featureId,
                "config_version" to configVersion.toString(),
            ),
        )
    }

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

    fun disabled(
        feature: HookFeature,
        environment: HookEnvironment,
        reason: String = "preference_disabled",
    ) {
        logger.info(
            event = "feature.config.disabled",
            fields = featureFields(feature, environment) + ("reason" to reason),
        )
    }

    fun duplicate(feature: HookFeature, environment: HookEnvironment) {
        logger.info(
            event = "hook.install.skipped",
            fields = featureFields(feature, environment) + ("reason" to "already_attempted"),
        )
    }

    fun retryScheduled(
        feature: HookFeature,
        environment: HookEnvironment,
        record: HookInstallRecord,
        delayMillis: Long,
    ) {
        logger.warn(
            event = "hook.install.retry.scheduled",
            fields = featureFields(feature, environment) + mapOf(
                "attempt_count" to record.attemptCount.toString(),
                "retry_count" to record.retryCount.toString(),
                "delay_ms" to delayMillis.toString(),
                "failure_stage" to record.lastFailureStage.orEmpty(),
            ),
        )
    }

    fun settingsRetryScheduled(
        environment: HookEnvironment,
        retryCount: Int,
        delayMillis: Long,
    ) {
        logger.warn(
            event = "config.snapshot.retry.scheduled",
            fields = environmentFields(environment) + mapOf(
                "retry_count" to retryCount.toString(),
                "delay_ms" to delayMillis.toString(),
            ),
        )
    }

    fun settingsRetryExhausted(environment: HookEnvironment) {
        logger.error(
            event = "config.snapshot.retry.exhausted",
            fields = environmentFields(environment),
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

    fun deferred(
        feature: HookFeature,
        environment: HookEnvironment,
        reason: String,
    ) {
        logger.info(
            event = "hook.install.deferred",
            fields = featureFields(feature, environment) + mapOf(
                "reason" to reason,
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
