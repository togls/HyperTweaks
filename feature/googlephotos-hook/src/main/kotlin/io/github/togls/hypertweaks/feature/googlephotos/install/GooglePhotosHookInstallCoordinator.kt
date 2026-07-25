package io.github.togls.hypertweaks.feature.googlephotos.install

import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.aggregateHookInstallResult
import io.github.togls.hypertweaks.core.xposed.rethrowIfFatal
import io.github.togls.hypertweaks.feature.googlephotos.resolver.UnsupportedGooglePhotosTargetException

internal enum class GooglePhotosInstallTarget(
    val logName: String,
    val isStrategy: Boolean,
    val isCoreCoordinateCapability: Boolean = false,
) {
    LIFECYCLE("lifecycle", false),
    MAP_VIEW("map_view", false),
    MARKER_API("marker_api", true, true),
    MARKER_ANIMATION("marker_animation", true),
    INITIAL_PREVIEW_SELECTION("initial_preview_selection", true),
    MAP_LOCATION("map_location", true),
    CAMERA_UPDATE("camera_update", true),
    HEATMAP_INDEX("heatmap_index", true, true),
    S2_QUERY("s2_query", true),
}

internal data class GooglePhotosHookInstallStep(
    val target: GooglePhotosInstallTarget,
    val enabled: Boolean = true,
    val disabledReason: String = "RELEASE_DIAGNOSTICS_DISABLED",
    val install: () -> Unit,
)

internal sealed interface GooglePhotosTargetInstallOutcome {
    data object Installed : GooglePhotosTargetInstallOutcome

    data class Skipped(
        val reason: String,
    ) : GooglePhotosTargetInstallOutcome

    data class Failed(
        val error: Throwable,
    ) : GooglePhotosTargetInstallOutcome
}

internal data class GooglePhotosHookInstallResult(
    private val outcomes: Map<GooglePhotosInstallTarget, GooglePhotosTargetInstallOutcome>,
) {
    fun installed(target: GooglePhotosInstallTarget): Boolean {
        return outcomes[target] is GooglePhotosTargetInstallOutcome.Installed
    }

    fun outcome(target: GooglePhotosInstallTarget): GooglePhotosTargetInstallOutcome? {
        return outcomes[target]
    }

    fun toHookInstallResult(): HookInstallResult {
        val installedTargets = outcomes.filterValues {
            it is GooglePhotosTargetInstallOutcome.Installed
        }.keys.mapTo(mutableSetOf(), GooglePhotosInstallTarget::logName)
        val failedTargets = outcomes.mapNotNull { (target, outcome) ->
            (outcome as? GooglePhotosTargetInstallOutcome.Failed)
                ?.let { target.logName to it.error }
        }.toMap()
        val hasCoreCoordinateCapability = outcomes.any { (target, outcome) ->
            target.isCoreCoordinateCapability &&
                outcome is GooglePhotosTargetInstallOutcome.Installed
        }
        val result = aggregateHookInstallResult(
            installedTargets = installedTargets,
            failedTargets = failedTargets,
            unsupportedReason = "No core Google Photos Marker or heatmap capability was installed",
            hasUsableInstalledTarget = hasCoreCoordinateCapability,
        )
        // 辅助 Probe 已注册却没有核心坐标能力时，无法安全自动重试，否则会重复注册回调。
        return if (result is HookInstallResult.Failed && installedTargets.isNotEmpty()) {
            result.copy(retryable = false)
        } else {
            result
        }
    }
}

internal class GooglePhotosHookInstallCoordinator(
    private val onBegin: (GooglePhotosInstallTarget) -> Unit = {},
    private val onSuccess: (GooglePhotosInstallTarget) -> Unit = {},
    private val onSkipped: (GooglePhotosInstallTarget, String) -> Unit = { _, _ -> },
    private val onFailure: (GooglePhotosInstallTarget, Throwable) -> Unit = { _, _ -> },
) {
    fun install(vararg steps: GooglePhotosHookInstallStep): GooglePhotosHookInstallResult {
        val outcomes = linkedMapOf<GooglePhotosInstallTarget, GooglePhotosTargetInstallOutcome>()
        steps.forEach { step -> outcomes[step.target] = installStep(step) }
        return GooglePhotosHookInstallResult(outcomes)
    }

    private fun installStep(step: GooglePhotosHookInstallStep): GooglePhotosTargetInstallOutcome {
        if (!step.enabled) {
            onSkipped(step.target, step.disabledReason)
            return GooglePhotosTargetInstallOutcome.Skipped(step.disabledReason)
        }
        onBegin(step.target)
        return try {
            step.install()
            onSuccess(step.target)
            GooglePhotosTargetInstallOutcome.Installed
        } catch (error: UnsupportedGooglePhotosTargetException) {
            onSkipped(step.target, error.message.orEmpty())
            GooglePhotosTargetInstallOutcome.Skipped(error.message.orEmpty())
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            onFailure(step.target, error)
            GooglePhotosTargetInstallOutcome.Failed(error)
        }
    }
}
