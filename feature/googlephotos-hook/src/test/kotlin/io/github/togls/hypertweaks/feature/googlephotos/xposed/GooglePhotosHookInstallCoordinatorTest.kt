package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallCoordinator
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallStep
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosInstallTarget
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosTargetInstallOutcome
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTarget
import io.github.togls.hypertweaks.feature.googlephotos.resolver.UnsupportedGooglePhotosTargetException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosHookInstallCoordinatorTest {

    @Test
    fun previewCallbackCoordinateMutationStrategyIsNotInstalled() {
        assertFalse(
            GooglePhotosInstallTarget.entries.any { target -> target.name == "PREVIEW_MARKER" },
        )
    }

    @Test
    fun sharedPhotoIndexMutationStrategyIsNotInstalled() {
        assertFalse(
            GooglePhotosInstallTarget.entries.any { target -> target.name == "S2_INDEX" },
        )
    }

    @Test
    fun markerAnimationStrategyIsInstalledIndependently() {
        assertTrue(
            GooglePhotosInstallTarget.entries.any { target -> target.name == "MARKER_ANIMATION" },
        )
    }

    @Test
    fun failedStrategyDoesNotInterruptOtherInstallTargets() {
        val attemptedTargets = mutableListOf<GooglePhotosInstallTarget>()
        val failedTargets = mutableListOf<GooglePhotosInstallTarget>()
        val coordinator = GooglePhotosHookInstallCoordinator(
            onFailure = { target, _ -> failedTargets += target },
        )

        val result = coordinator.install(
            step(GooglePhotosInstallTarget.LIFECYCLE, attemptedTargets),
            step(GooglePhotosInstallTarget.MAP_VIEW, attemptedTargets),
            failingStep(GooglePhotosInstallTarget.MARKER_API, attemptedTargets),
            step(GooglePhotosInstallTarget.MARKER_ANIMATION, attemptedTargets),
            step(GooglePhotosInstallTarget.INITIAL_PREVIEW_SELECTION, attemptedTargets),
            step(GooglePhotosInstallTarget.MAP_LOCATION, attemptedTargets),
            step(GooglePhotosInstallTarget.CAMERA_UPDATE, attemptedTargets),
            step(GooglePhotosInstallTarget.HEATMAP_INDEX, attemptedTargets),
            step(GooglePhotosInstallTarget.S2_QUERY, attemptedTargets),
        )

        assertEquals(GooglePhotosInstallTarget.entries, attemptedTargets)
        assertEquals(listOf(GooglePhotosInstallTarget.MARKER_API), failedTargets)
        assertFalse(result.installed(GooglePhotosInstallTarget.MARKER_API))
        assertTrue(
            result.outcome(GooglePhotosInstallTarget.MARKER_API) is
                GooglePhotosTargetInstallOutcome.Failed,
        )
        assertTrue(result.installed(GooglePhotosInstallTarget.LIFECYCLE))
        assertTrue(result.installed(GooglePhotosInstallTarget.MAP_VIEW))
        assertTrue(result.installed(GooglePhotosInstallTarget.MARKER_ANIMATION))
        assertTrue(result.installed(GooglePhotosInstallTarget.INITIAL_PREVIEW_SELECTION))
        assertTrue(result.installed(GooglePhotosInstallTarget.MAP_LOCATION))
        assertTrue(result.installed(GooglePhotosInstallTarget.CAMERA_UPDATE))
        assertTrue(result.installed(GooglePhotosInstallTarget.HEATMAP_INDEX))
        assertTrue(result.installed(GooglePhotosInstallTarget.S2_QUERY))
    }

    @Test
    fun disabledDiagnosticTargetIsSkippedWithoutInstalling() {
        var installed = false
        val skippedTargets = mutableListOf<GooglePhotosInstallTarget>()
        val coordinator = GooglePhotosHookInstallCoordinator(
            onSkipped = { target, _ -> skippedTargets += target },
        )

        val result = coordinator.install(
            GooglePhotosHookInstallStep(
                target = GooglePhotosInstallTarget.MAP_VIEW,
                enabled = false,
            ) {
                installed = true
            },
        )

        assertFalse(installed)
        assertFalse(result.installed(GooglePhotosInstallTarget.MAP_VIEW))
        assertTrue(
            result.outcome(GooglePhotosInstallTarget.MAP_VIEW) is
                GooglePhotosTargetInstallOutcome.Skipped,
        )
        assertEquals(listOf(GooglePhotosInstallTarget.MAP_VIEW), skippedTargets)
    }

    @Test
    fun allStrategyFailuresAggregateToFailed() {
        val markerFailure = IllegalStateException("marker install failed")
        val heatmapFailure = IllegalArgumentException("heatmap install failed")
        val result = GooglePhotosHookInstallCoordinator().install(
            failingStep(GooglePhotosInstallTarget.MARKER_API, markerFailure),
            failingStep(GooglePhotosInstallTarget.HEATMAP_INDEX, heatmapFailure),
        ).toHookInstallResult()

        assertTrue(result is HookInstallResult.Failed)
        result as HookInstallResult.Failed
        assertSame(heatmapFailure, result.error.cause)
        assertEquals(1, result.error.suppressed.size)
    }

    @Test
    fun allSkippedTargetsAggregateToUnsupported() {
        val result = GooglePhotosHookInstallCoordinator().install(
            GooglePhotosHookInstallStep(
                target = GooglePhotosInstallTarget.MAP_VIEW,
                enabled = false,
            ) {},
            unsupportedStep(
                GooglePhotosInstallTarget.MARKER_API,
                GooglePhotosTarget.MAP_EXPLORE_ACTIVITY,
            ),
        ).toHookInstallResult()

        assertTrue(result is HookInstallResult.Unsupported)
    }

    @Test
    fun partialStrategySuccessExposesActualInstalledAndFailedTargets() {
        val cameraFailure = IllegalStateException("camera install failed")
        val result = GooglePhotosHookInstallCoordinator().install(
            step(GooglePhotosInstallTarget.LIFECYCLE),
            step(GooglePhotosInstallTarget.MARKER_API),
            failingStep(GooglePhotosInstallTarget.CAMERA_UPDATE, cameraFailure),
            unsupportedStep(
                GooglePhotosInstallTarget.HEATMAP_INDEX,
                GooglePhotosTarget.S2_BUILDER,
            ),
        ).toHookInstallResult()

        assertTrue(result is HookInstallResult.Installed)
        result as HookInstallResult.Installed
        assertEquals(setOf("lifecycle", "marker_api"), result.installedTargets)
        assertEquals(setOf("camera_update"), result.failedTargets)
    }

    private fun step(
        target: GooglePhotosInstallTarget,
        attemptedTargets: MutableList<GooglePhotosInstallTarget>,
    ): GooglePhotosHookInstallStep {
        return GooglePhotosHookInstallStep(target) { attemptedTargets += target }
    }

    private fun failingStep(
        target: GooglePhotosInstallTarget,
        attemptedTargets: MutableList<GooglePhotosInstallTarget>,
    ): GooglePhotosHookInstallStep {
        return GooglePhotosHookInstallStep(target) {
            attemptedTargets += target
            error("expected install failure")
        }
    }

    private fun step(target: GooglePhotosInstallTarget): GooglePhotosHookInstallStep {
        return GooglePhotosHookInstallStep(target) {}
    }

    private fun failingStep(
        target: GooglePhotosInstallTarget,
        error: Throwable,
    ): GooglePhotosHookInstallStep {
        return GooglePhotosHookInstallStep(target) { throw error }
    }

    private fun unsupportedStep(
        target: GooglePhotosInstallTarget,
        unresolvedTarget: GooglePhotosTarget,
    ): GooglePhotosHookInstallStep {
        return GooglePhotosHookInstallStep(target) {
            throw UnsupportedGooglePhotosTargetException(
                unresolvedTarget,
                "Unable to resolve ${unresolvedTarget.logName}",
            )
        }
    }
}
