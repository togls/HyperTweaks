package io.github.togls.hypertweaks.feature.googlephotos.coordinate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChinaCoordinateConverterTest {

    @Test
    fun convertsShenzhenCoordinateToGcj02() {
        val converted = ChinaCoordinateConverter.wgs84ToGcj02(
            latitude = 22.543096,
            longitude = 114.057865,
        )

        assertEquals(22.540378, converted.latitude, 0.000001)
        assertEquals(114.062978, converted.longitude, 0.000001)
    }

    @Test
    fun leavesCoordinateOutsideMainlandChinaUnchanged() {
        val original = Coordinate(51.5074, -0.1278)

        assertEquals(
            original,
            ChinaCoordinateConverter.wgs84ToGcj02(original.latitude, original.longitude),
        )
    }

    @Test
    fun leavesInvalidCoordinatesUnchanged() {
        val nanResult = ChinaCoordinateConverter.wgs84ToGcj02(Double.NaN, 114.0)
        val positiveInfinityResult = ChinaCoordinateConverter.wgs84ToGcj02(
            22.0,
            Double.POSITIVE_INFINITY,
        )

        assertTrue(nanResult.latitude.isNaN())
        assertEquals(114.0, nanResult.longitude, 0.0)
        assertEquals(22.0, positiveInfinityResult.latitude, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, positiveInfinityResult.longitude, 0.0)
        assertEquals(Coordinate(91.0, 114.0), ChinaCoordinateConverter.wgs84ToGcj02(91.0, 114.0))
    }

    @Test
    fun conversionFailureReturnsOriginalCoordinate() {
        val original = Coordinate(22.543096, 114.057865)

        val result = ChinaCoordinateConverter.convertSafely(
            latitude = original.latitude,
            longitude = original.longitude,
        ) { _, _ ->
            throw IllegalStateException("simulated conversion failure")
        }

        assertEquals(original, result.coordinate)
        assertTrue(result.failure is IllegalStateException)
    }
}
