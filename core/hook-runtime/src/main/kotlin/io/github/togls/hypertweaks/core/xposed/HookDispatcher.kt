package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.Logger

class HookDispatcher(
    private val catalog: HookFeatureCatalog,
    private val engine: HookEngine,
    private val settingsProvider: HookSettingsProvider,
    private val installGuard: HookInstallGuard,
    logger: Logger,
) {
    private val installLogger = HookInstallLogger(logger)

    fun dispatch(environment: HookEnvironment): List<HookFeatureDispatchResult> {
        installLogger.registryStarted(environment)
        val settingsState = settingsProvider.currentState
        if (settingsState is HookSettingsState.Unavailable) {
            installLogger.settingsUnavailable(environment, settingsState.reason)
            return emptyList()
        }
        val settings = (settingsState as HookSettingsState.Ready).snapshot
        return catalog.matching(environment).map { feature ->
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
            logger = installLogger.loggerFor(feature.id),
            installGuard = installGuard,
            settingsProvider = settingsProvider,
        )
        return try {
            handleInstallResult(feature, environment, key, feature.install(context))
        } catch (error: Throwable) {
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
            is HookInstallResult.Failed -> {
                handleInstallFailure(feature, environment, key, result.error)
            }
        }
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
        installGuard.markFailed(key)
        installLogger.unsupported(feature, environment, result.reason)
        return HookFeatureDispatchResult.Unsupported(feature.id, result.reason)
    }

    private fun handleInstallFailure(
        feature: HookFeature,
        environment: HookEnvironment,
        key: HookInstallKey,
        error: Throwable,
    ): HookFeatureDispatchResult.Failed {
        installGuard.markFailed(key)
        installLogger.failed(feature, environment, error)
        return HookFeatureDispatchResult.Failed(feature.id, error)
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

    data class Failed(
        override val featureId: String,
        val error: Throwable,
    ) : HookFeatureDispatchResult
}
