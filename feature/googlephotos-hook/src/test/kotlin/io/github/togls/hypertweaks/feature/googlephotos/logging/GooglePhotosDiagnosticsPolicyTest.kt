package io.github.togls.hypertweaks.feature.googlephotos.logging

import io.github.togls.hypertweaks.googlephotos.hook.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosDiagnosticsPolicyTest {
    @Test
    fun currentBuildPolicyMatchesGeneratedBuildType() {
        assertEquals(
            BuildConfig.DEBUG,
            GooglePhotosDiagnosticsPolicy.forCurrentBuild().highFrequencyProbesEnabled,
        )
    }

    @Test
    fun debugBuildKeepsDetailedAndSummaryDiagnostics() {
        val policy = GooglePhotosDiagnosticsPolicy.forBuild(debug = true)

        assertTrue(policy.shouldCaptureStack(1, 5, 100))
        assertTrue(policy.shouldCaptureStack(3, 5, 100))
        assertTrue(policy.shouldCaptureStack(500, 5, 100))
        assertFalse(policy.shouldCaptureStack(4, 5, 100))
        assertFalse(policy.shouldCaptureStack(100, 5, 100))
    }

    @Test
    fun releaseBuildDisablesHighFrequencyDiagnostics() {
        val policy = GooglePhotosDiagnosticsPolicy.forBuild(debug = false)

        assertFalse(policy.highFrequencyProbesEnabled)
        assertFalse(policy.shouldCaptureStack(1, 5, 100))
        assertFalse(policy.shouldCaptureStack(100, 5, 100))
    }
}
