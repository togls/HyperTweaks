package io.github.togls.hypertweaks.core.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallResultAggregatorTest {
    @Test
    fun partialSuccessReturnsInstalledWithRealTargets() {
        val result = aggregateHookInstallResult(
            installedTargets = setOf("marker_api"),
            failedTargets = mapOf("s2_query" to IllegalStateException("missing method")),
            unsupportedReason = "unsupported",
        )

        assertTrue(result is HookInstallResult.Installed)
        result as HookInstallResult.Installed
        assertEquals(setOf("marker_api"), result.installedTargets)
        assertEquals(setOf("s2_query"), result.failedTargets)
    }

    @Test
    fun failuresWithoutUsableTargetReturnStructuredFailure() {
        val failure = IllegalStateException("missing method")

        val result = aggregateHookInstallResult(
            installedTargets = emptySet(),
            failedTargets = mapOf("s2_query" to failure),
            unsupportedReason = "unsupported",
        )

        assertTrue(result is HookInstallResult.Failed)
        result as HookInstallResult.Failed
        assertSame(failure, result.error.cause)
    }

    @Test
    fun noSuccessOrFailureReturnsUnsupported() {
        val result = aggregateHookInstallResult(
            installedTargets = emptySet(),
            failedTargets = emptyMap(),
            unsupportedReason = "no compatible targets",
        )

        assertEquals(
            HookInstallResult.Unsupported("no compatible targets"),
            result,
        )
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalFailureIsRethrown() {
        aggregateHookInstallResult(
            installedTargets = emptySet(),
            failedTargets = mapOf("target" to OutOfMemoryError("fatal")),
            unsupportedReason = "unsupported",
        )
    }
}
