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
        assertFalse(CoordinateValidator.isInMainlandChina(22.3193, 114.1694))
        assertFalse(CoordinateValidator.isInMainlandChina(22.5285, 114.1133))
        assertFalse(CoordinateValidator.isInMainlandChina(22.5455, 114.2037))
        assertTrue(CoordinateValidator.isInMainlandChina(22.543096, 114.057865))
        assertTrue(CoordinateValidator.isInMainlandChina(22.5998, 114.2784))
    }
}
