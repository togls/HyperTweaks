package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            step(GooglePhotosInstallTarget.S2_QUERY, attemptedTargets),
        )

        assertEquals(GooglePhotosInstallTarget.entries, attemptedTargets)
        assertEquals(listOf(GooglePhotosInstallTarget.MARKER_API), failedTargets)
        assertFalse(result.installed(GooglePhotosInstallTarget.MARKER_API))
        assertTrue(result.installed(GooglePhotosInstallTarget.LIFECYCLE))
        assertTrue(result.installed(GooglePhotosInstallTarget.MAP_VIEW))
        assertTrue(result.installed(GooglePhotosInstallTarget.MARKER_ANIMATION))
        assertTrue(result.installed(GooglePhotosInstallTarget.INITIAL_PREVIEW_SELECTION))
        assertTrue(result.installed(GooglePhotosInstallTarget.MAP_LOCATION))
        assertTrue(result.installed(GooglePhotosInstallTarget.CAMERA_UPDATE))
        assertTrue(result.installed(GooglePhotosInstallTarget.S2_QUERY))
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
}
