package io.github.togls.hypertweaks.feature.googlephotos.logging

import io.github.togls.hypertweaks.googlephotos.hook.BuildConfig

internal data class GooglePhotosDiagnosticsPolicy(
    val highFrequencyProbesEnabled: Boolean,
) {
    fun shouldCaptureStack(callCount: Int, detailedCallLimit: Int, summaryInterval: Int): Boolean {
        if (!highFrequencyProbesEnabled) return false
        val boundedDetailedLimit = minOf(detailedCallLimit, MaximumDetailedStacks)
        val boundedSummaryInterval = maxOf(summaryInterval, StackSummaryInterval)
        return callCount <= boundedDetailedLimit || callCount % boundedSummaryInterval == 0
    }

    companion object {
        private const val MaximumDetailedStacks = 3
        private const val StackSummaryInterval = 500

        fun forCurrentBuild(): GooglePhotosDiagnosticsPolicy {
            return forBuild(BuildConfig.DEBUG)
        }

        fun forBuild(debug: Boolean): GooglePhotosDiagnosticsPolicy {
            return GooglePhotosDiagnosticsPolicy(highFrequencyProbesEnabled = debug)
        }
    }
}
