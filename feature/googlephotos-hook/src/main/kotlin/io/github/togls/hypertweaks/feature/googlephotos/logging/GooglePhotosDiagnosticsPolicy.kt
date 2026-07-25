package io.github.togls.hypertweaks.feature.googlephotos.logging

import io.github.togls.hypertweaks.googlephotos.hook.BuildConfig

internal data class GooglePhotosDiagnosticsPolicy(
    val highFrequencyProbesEnabled: Boolean,
) {
    fun shouldCaptureStack(callCount: Int, detailedCallLimit: Int, summaryInterval: Int): Boolean {
        if (!highFrequencyProbesEnabled) return false
        return callCount <= detailedCallLimit || callCount % summaryInterval == 0
    }

    companion object {
        fun forCurrentBuild(): GooglePhotosDiagnosticsPolicy {
            return forBuild(BuildConfig.DEBUG)
        }

        fun forBuild(debug: Boolean): GooglePhotosDiagnosticsPolicy {
            return GooglePhotosDiagnosticsPolicy(highFrequencyProbesEnabled = debug)
        }
    }
}
