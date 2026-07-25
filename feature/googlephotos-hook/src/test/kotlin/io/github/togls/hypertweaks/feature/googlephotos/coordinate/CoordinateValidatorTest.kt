package io.github.togls.hypertweaks.feature.googlephotos.coordinate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateValidatorTest {

    @Test
    fun acceptsFiniteCoordinatesInsideWorldBounds() {
        assertTrue(CoordinateValidator.isValid(22.543096, 114.057865))
        assertTrue(CoordinateValidator.isValid(-90.0, -180.0))
        assertTrue(CoordinateValidator.isValid(90.0, 180.0))
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFalse(CoordinateValidator.isValid(Double.NaN, 114.0))
        assertFalse(CoordinateValidator.isValid(22.0, Double.NaN))
        assertFalse(CoordinateValidator.isValid(Double.POSITIVE_INFINITY, 114.0))
        assertFalse(CoordinateValidator.isValid(22.0, Double.NEGATIVE_INFINITY))
        assertFalse(CoordinateValidator.isValid(-90.0001, 114.0))
        assertFalse(CoordinateValidator.isValid(90.0001, 114.0))
        assertFalse(CoordinateValidator.isValid(22.0, -180.0001))
        assertFalse(CoordinateValidator.isValid(22.0, 180.0001))
    }

    @Test
    fun mainlandBoundsAreExplicitAndInclusive() {
        assertTrue(CoordinateValidator.isInMainlandChina(0.8293, 72.004))
        assertTrue(CoordinateValidator.isInMainlandChina(55.8271, 137.8347))
        assertFalse(CoordinateValidator.isInMainlandChina(0.8292, 100.0))
        assertFalse(CoordinateValidator.isInMainlandChina(30.0, 137.8348))
    }

    @Test
    fun excludesHongKongWithoutExcludingNearbyShenzhen() {
        listOf(
            22.3193 to 114.1694,
            22.5285 to 114.1133,
            22.5455 to 114.2037,
            22.1987 to 113.5439,
            22.1567 to 113.5525,
            25.0330 to 121.5654,
            22.6273 to 120.3014,
            23.5711 to 119.5793,
        ).forEach { (latitude, longitude) ->
            assertFalse(CoordinateValidator.isInMainlandChina(latitude, longitude))
        }
        listOf(
            22.543096 to 114.057865,
            22.5998 to 114.2784,
            22.2707 to 113.5767,
        ).forEach { (latitude, longitude) ->
            assertTrue(CoordinateValidator.isInMainlandChina(latitude, longitude))
        }
    }
}
