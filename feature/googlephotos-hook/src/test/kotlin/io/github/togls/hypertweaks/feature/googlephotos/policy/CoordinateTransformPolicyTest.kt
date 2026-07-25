package io.github.togls.hypertweaks.feature.googlephotos.policy

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateTransformPolicyTest {
    private val policy = CoordinateTransformPolicy { latitude, longitude ->
        Coordinate(latitude + 1.0, longitude + 2.0)
    }

    @Test
    fun transformsMainlandCoordinatesWithSharedPolicy() {
        val result = policy.transform(Coordinate(22.543096, 114.057865))

        assertEquals(CoordinateTransformOutcome.CONVERTED, result.outcome)
        assertEquals(Coordinate(23.543096, 116.057865), result.converted)
    }

    @Test
    fun excludesHongKongMacauAndTaiwan() {
        val excludedCoordinates = listOf(
            Coordinate(22.3193, 114.1694),
            Coordinate(22.1987, 113.5439),
            Coordinate(25.0330, 121.5654),
            Coordinate(23.5711, 119.5793),
        )

        excludedCoordinates.forEach { coordinate ->
            val result = policy.transform(coordinate)
            assertEquals(CoordinateTransformOutcome.UNCHANGED, result.outcome)
            assertEquals("OUTSIDE_CHINA", result.reason)
            assertEquals(coordinate, result.converted)
        }
    }

    @Test
    fun reportsInvalidAndConversionFailuresExplicitly() {
        val invalid = policy.transform(Coordinate(Double.NaN, 114.0))
        val failingPolicy = CoordinateTransformPolicy { _, _ -> error("conversion failed") }
        val failed = failingPolicy.transform(Coordinate(22.543096, 114.057865))

        assertEquals(CoordinateTransformOutcome.UNCHANGED, invalid.outcome)
        assertEquals("INVALID_COORDINATE", invalid.reason)
        assertEquals(CoordinateTransformOutcome.FAILED, failed.outcome)
        assertEquals("CONVERSION_FAILED", failed.reason)
        assertNull(failed.converted)
        assertEquals("conversion failed", failed.failure?.message)
    }
}
