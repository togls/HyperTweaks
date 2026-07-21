package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePhotosHeatmapIndexHookTest {

    @Test
    fun findsSynchronizedS2IndexBatchMethodByStructure() {
        val method = GooglePhotosHeatmapIndexMethodMatcher.find(FakeS2IndexBuilder::class.java)

        assertNotNull(method)
        assertEquals("addItems", method?.name)
    }

    @Test
    fun failsClosedWhenBatchMethodIsNotSynchronized() {
        val method = GooglePhotosHeatmapIndexMethodMatcher.find(UnsafeS2IndexBuilder::class.java)

        assertNull(method)
    }

    @Test
    fun transformsOnlyPopulatedCoordinatesInBatch() {
        val latitudes = floatArrayOf(22.5f, 31.2f, 88.0f)
        val longitudes = floatArrayOf(114.0f, 121.4f, 99.0f)

        val result = HeatmapCoordinateBatchTransformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 2,
            converter = { latitude, longitude ->
                Coordinate(latitude + 1.0, longitude + 2.0)
            },
        )

        assertNull(result.failure)
        assertEquals(2, result.convertedCount)
        assertArrayEquals(floatArrayOf(23.5f, 32.2f, 88.0f), latitudes, 0.0001f)
        assertArrayEquals(floatArrayOf(116.0f, 123.4f, 99.0f), longitudes, 0.0001f)
    }

    @Test
    fun invalidBatchSizeLeavesArraysUntouched() {
        val originalLatitudes = floatArrayOf(22.5f)
        val originalLongitudes = floatArrayOf(114.0f)
        val latitudes = originalLatitudes.copyOf()
        val longitudes = originalLongitudes.copyOf()

        val result = HeatmapCoordinateBatchTransformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 2,
        )

        assertNotNull(result.failure)
        assertArrayEquals(originalLatitudes, latitudes, 0.0f)
        assertArrayEquals(originalLongitudes, longitudes, 0.0f)
    }

    @Test
    fun converterFailureLeavesWholeBatchUntouched() {
        val originalLatitudes = floatArrayOf(22.5f, 31.2f)
        val originalLongitudes = floatArrayOf(114.0f, 121.4f)
        val latitudes = originalLatitudes.copyOf()
        val longitudes = originalLongitudes.copyOf()

        val result = HeatmapCoordinateBatchTransformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 2,
            converter = { latitude, longitude ->
                if (latitude > 30.0) error("conversion failed")
                Coordinate(latitude + 1.0, longitude + 2.0)
            },
        )

        assertNotNull(result.failure)
        assertEquals(0, result.convertedCount)
        assertArrayEquals(originalLatitudes, latitudes, 0.0f)
        assertArrayEquals(originalLongitudes, longitudes, 0.0f)
    }

    @Test
    fun inactiveSessionLeavesBatchUntouched() {
        val originalLatitudes = floatArrayOf(22.543096f)
        val originalLongitudes = floatArrayOf(114.057865f)
        val latitudes = originalLatitudes.copyOf()
        val longitudes = originalLongitudes.copyOf()
        val transformer = SessionScopedHeatmapTransformer(
            converter = { latitude, longitude -> Coordinate(latitude + 1.0, longitude + 2.0) },
        )

        val result = transformer.transform(null, Any(), latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("NO_ACTIVE_SESSION", result.reason)
        assertArrayEquals(originalLatitudes, latitudes, 0.0f)
        assertArrayEquals(originalLongitudes, longitudes, 0.0f)
    }

    @Test
    fun sameBuilderIsConvertedOnlyOncePerSession() {
        val transformer = SessionScopedHeatmapTransformer(
            converter = { latitude, longitude -> Coordinate(latitude + 1.0, longitude + 2.0) },
        )
        val builder = Any()
        val latitudes = floatArrayOf(22.543096f)
        val longitudes = floatArrayOf(114.057865f)

        val first = transformer.transform(7L, builder, latitudes, longitudes, 1)
        val convertedLatitude = latitudes.single()
        val duplicate = transformer.transform(7L, builder, latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.CONVERTED, first.outcome)
        assertEquals("ALREADY_CONVERTED", duplicate.reason)
        assertEquals(convertedLatitude, latitudes.single(), 0.0f)
    }

    @Test
    fun mismatchedArraysAreSkippedWithoutMutation() {
        val latitudes = floatArrayOf(22.5f, 31.2f)
        val longitudes = floatArrayOf(114.0f)
        val transformer = SessionScopedHeatmapTransformer()

        val result = transformer.transform(1L, Any(), latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("ARRAY_SIZE_MISMATCH", result.reason)
        assertArrayEquals(floatArrayOf(22.5f, 31.2f), latitudes, 0.0f)
        assertArrayEquals(floatArrayOf(114.0f), longitudes, 0.0f)
    }

    @Test
    fun outsideChinaBatchRemainsUnchanged() {
        val latitudes = floatArrayOf(35.6762f)
        val longitudes = floatArrayOf(139.6503f)
        val transformer = SessionScopedHeatmapTransformer()

        val result = transformer.transform(1L, Any(), latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("NO_CHINA_COORDINATES", result.reason)
        assertEquals(0, result.batchResult.chinaCount)
        assertEquals(0, result.batchResult.convertedCount)
        assertNull(result.failure)
        assertEquals(35.6762f, latitudes.single(), 0.0f)
    }

    private class FakeS2IndexBuilder {
        @Synchronized
        @Suppress("UNUSED_PARAMETER")
        fun addItems(
            ids: LongArray,
            latitudes: FloatArray,
            longitudes: FloatArray,
            timestamps: LongArray,
            itemCount: Int,
        ) = Unit
    }

    private class UnsafeS2IndexBuilder {
        @Suppress("UNUSED_PARAMETER")
        fun addItems(
            ids: LongArray,
            latitudes: FloatArray,
            longitudes: FloatArray,
            timestamps: LongArray,
            itemCount: Int,
        ) = Unit
    }
}
