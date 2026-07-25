package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HookDispatcher(
    private val catalog: HookFeatureCatalog,
    private val engine: HookEngine,
    private val settingsProvider: HookSettingsProvider,
    private val installGuard: HookInstallGuard,
    logger: Logger,
    private val retryScheduler: HookRetryScheduler = ProcessHookRetryScheduler(),
) {
    private val installLogger = HookInstallLogger(logger)
    private val settingsRetryCounts = ConcurrentHashMap<EnvironmentKey, AtomicInteger>()
    private val knownEnvironments = ConcurrentHashMap<EnvironmentKey, HookEnvironment>()
    @Suppress("unused")
    private val settingsSubscription = settingsProvider.subscribe { state ->
        if (state is HookSettingsState.Ready) {
            knownEnvironments.values.forEach { environment ->
                retryScheduler.schedule(0L) { dispatch(environment) }
            }
        }
    }

    fun dispatch(environment: HookEnvironment): List<HookFeatureDispatchResult> {
        knownEnvironments[environment.key()] = environment
        installLogger.registryStarted(environment)
        settingsProvider.refreshIfUnavailable()
        val settingsState = settingsProvider.currentState
        if (settingsState is HookSettingsState.Unavailable) {
            installLogger.settingsUnavailable(environment, settingsState.reason)
            if (settingsState.retryable) scheduleSettingsRetry(environment)
            return emptyList()
        }
        settingsRetryCounts.remove(environment.key())
        val settings = (settingsState as HookSettingsState.Ready).snapshot
        val matchingFeatures = catalog.matching(environment)
        if (environment.isSystemServer && !settings.systemServerFeaturesEnabled) {
            return matchingFeatures.map { feature ->
                installLogger.disabled(feature, environment, "system_server_safe_mode")
                HookFeatureDispatchResult.Disabled(feature.id)
            }
        }
        return matchingFeatures.map { feature ->
            dispatchFeature(feature, environment, settings)
        }
    }

    private fun dispatchFeature(
        feature: HookFeature,
        environment: HookEnvironment,
        settings: HookSettingsSnapshot,
    ): HookFeatureDispatchResult {
        installLogger.matchStarted(feature, environment)
        installLogger.matchSucceeded(feature, environment)
        if (!settings.isEnabled(feature.preferenceKey)) {
            installLogger.disabled(feature, environment)
            return HookFeatureDispatchResult.Disabled(feature.id)
        }
        val key = installKey(feature, environment)
        if (!installGuard.tryStart(key)) {
            installLogger.duplicate(feature, environment)
            return HookFeatureDispatchResult.Duplicate(feature.id)
        }
        return installFeature(feature, environment, settings, key)
    }

    private fun installFeature(
        feature: HookFeature,
        environment: HookEnvironment,
        settings: HookSettingsSnapshot,
        key: HookInstallKey,
    ): HookFeatureDispatchResult {
        installLogger.installStarted(feature, environment)
        val context = HookContext(
            environment = environment,
            engine = engine,
            settings = settings,
            logger = installLogger.loggerFor(feature.id, settings.version),
            installGuard = installGuard,
            settingsProvider = settingsProvider,
        )
        return try {
            handleInstallResult(feature, environment, key, feature.install(context))
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            handleInstallFailure(feature, environment, key, error)
        }
    }

    private fun handleInstallResult(
        feature: HookFeature,
        environment: HookEnvironment,
        key: HookInstallKey,
        result: HookInstallResult,
    ): HookFeatureDispatchResult {
        return when (result) {
            is HookInstallResult.Installed -> installed(feature, environment, key, result)
            is HookInstallResult.Unsupported -> unsupported(feature, environment, key, result)
            is HookInstallResult.Deferred -> deferred(feature, environment, key, result)
            is HookInstallResult.Failed -> {
                handleInstallFailure(
                    feature = feature,
                    environment = environment,
                    key = key,
                    error = result.error,
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun deferred(
        feature: HookFeature,
        environment: HookEnvironment,
        key: HookInstallKey,
        result: HookInstallResult.Deferred,
    ): HookFeatureDispatchResult.Deferred {
        installGuard.markDeferred(key)
        installLogger.deferred(feature, environment, result.reason)
        return HookFeatureDispatchResult.Deferred(feature.id, result.reason)
    }

    private fun installed(
        feature: HookFeature,
        environment: HookEnvironment,
        key: HookInstallKey,
        result: HookInstallResult.Installed,
    ): HookFeatureDispatchResult.Installed {
        installGuard.markInstalled(key)
        installLogger.installed(feature, environment, result)
        return HookFeatureDispatchResult.Installed(feature.id, result)
    }

    private fun unsupported(
        feature: HookFeature,
        environment: HookEnvironment,
        key: HookInstallKey,
        result: HookInstallResult.Unsupported,
    ): HookFeatureDispatchResult.Unsupported {
        installGuard.markFailed(
            key = key,
            retryable = false,
            failureStage = "target_resolution",
            failureMessage = result.reason,
        )
        installLogger.unsupported(feature, environment, result.reason)
        return HookFeatureDispatchResult.Unsupported(feature.id, result.reason)
    }

    private fun handleInstallFailure(
        feature: HookFeature,
        environment: HookEnvironment,
        key: HookInstallKey,
        error: Throwable,
        retryable: Boolean = true,
    ): HookFeatureDispatchResult.Failed {
        error.rethrowIfFatal()
        val retryDelayMillis = installGuard.markFailed(
            key = key,
            retryable = retryable,
            failureStage = "feature_install",
            failureMessage = error.message,
        )
        installLogger.failed(feature, environment, error)
        if (retryDelayMillis != null) {
            installLogger.retryScheduled(
                feature = feature,
                environment = environment,
                record = installGuard.record(key),
                delayMillis = retryDelayMillis,
            )
            retryScheduler.schedule(retryDelayMillis) {
                dispatch(environment)
            }
        }
        return HookFeatureDispatchResult.Failed(feature.id, error)
    }

    private fun scheduleSettingsRetry(environment: HookEnvironment) {
        val retryIndex = settingsRetryCounts.computeIfAbsent(environment.key()) {
            AtomicInteger()
        }.getAndIncrement()
        val retryDelayMillis = SettingsRetryBackoffMillis.getOrNull(retryIndex)
        if (retryDelayMillis == null) {
            installLogger.settingsRetryExhausted(environment)
            return
        }
        installLogger.settingsRetryScheduled(
            environment = environment,
            retryCount = retryIndex + 1,
            delayMillis = retryDelayMillis,
        )
        retryScheduler.schedule(retryDelayMillis) {
            dispatch(environment)
        }
    }

    private fun installKey(
        feature: HookFeature,
        environment: HookEnvironment,
    ): HookInstallKey {
        return HookInstallKey(
            featureId = feature.id,
            packageName = environment.packageName,
            processName = environment.processName,
            classLoaderIdentity = System.identityHashCode(environment.classLoader),
            targetId = environment.packageName,
        )
    }

    private fun HookEnvironment.key(): EnvironmentKey {
        return EnvironmentKey(
            packageName = packageName,
            processName = processName,
            classLoaderIdentity = System.identityHashCode(classLoader),
        )
    }

    private data class EnvironmentKey(
        val packageName: String,
        val processName: String,
        val classLoaderIdentity: Int,
    )

    private companion object {
        val SettingsRetryBackoffMillis = longArrayOf(100L, 500L, 2_000L)
    }
}

sealed interface HookFeatureDispatchResult {
    val featureId: String

    data class Installed(
        override val featureId: String,
        val result: HookInstallResult.Installed,
    ) : HookFeatureDispatchResult

    data class Disabled(
        override val featureId: String,
    ) : HookFeatureDispatchResult

    data class Duplicate(
        override val featureId: String,
    ) : HookFeatureDispatchResult

    data class Unsupported(
        override val featureId: String,
        val reason: String,
    ) : HookFeatureDispatchResult

    data class Deferred(
        override val featureId: String,
        val reason: String,
    ) : HookFeatureDispatchResult

    data class Failed(
        override val featureId: String,
        val error: Throwable,
    ) : HookFeatureDispatchResult
}
