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
    fun batchIsConvertedWithoutActiveMapSession() {
        val latitudes = floatArrayOf(22.543096f)
        val longitudes = floatArrayOf(114.057865f)
        val transformer = HeatmapCoordinateTransformer(
            converter = { latitude, longitude -> Coordinate(latitude + 1.0, longitude + 2.0) },
        )

        val result = transformer.transform(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.CONVERTED, result.outcome)
        assertEquals("WGS84_TO_GCJ02", result.reason)
        assertArrayEquals(floatArrayOf(23.543096f), latitudes, 0.0001f)
        assertArrayEquals(floatArrayOf(116.057865f), longitudes, 0.0001f)
    }

    @Test
    fun transformerConvertsMultipleDistinctBatches() {
        val transformer = HeatmapCoordinateTransformer(
            converter = { latitude, longitude ->
                Coordinate(
                    latitude + 1.0,
                    longitude + 2.0,
                )
            },
        )

        val firstLatitudes = floatArrayOf(22.543096f)

        val firstLongitudes = floatArrayOf(114.057865f)

        val secondLatitudes = floatArrayOf(23.129110f)

        val secondLongitudes = floatArrayOf(113.264385f)

        val first = transformer.transform(
            latitudes = firstLatitudes,
            longitudes = firstLongitudes,
            itemCount = 1,
        )

        val second = transformer.transform(
            latitudes = secondLatitudes,
            longitudes = secondLongitudes,
            itemCount = 1,
        )

        assertEquals(
            HeatmapConversionOutcome.CONVERTED,
            first.outcome,
        )

        assertEquals(
            HeatmapConversionOutcome.CONVERTED,
            second.outcome,
        )

        assertArrayEquals(
            floatArrayOf(23.543096f),
            firstLatitudes,
            0.0001f,
        )

        assertArrayEquals(
            floatArrayOf(24.129110f),
            secondLatitudes,
            0.0001f,
        )
    }

    @Test
    fun sameConvertedBatchIsNotConvertedTwice() {
        val transformer = HeatmapCoordinateTransformer(
            converter = { latitude, longitude ->
                Coordinate(
                    latitude + 1.0,
                    longitude + 2.0,
                )
            },
        )

        val latitudes = floatArrayOf(22.543096f)

        val longitudes = floatArrayOf(114.057865f)

        val first = transformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 1,
        )

        val convertedLatitude = latitudes.single()
        val convertedLongitude = longitudes.single()

        val duplicate = transformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 1,
        )

        assertEquals(
            HeatmapConversionOutcome.CONVERTED,
            first.outcome,
        )

        assertEquals(
            HeatmapConversionOutcome.SKIPPED,
            duplicate.outcome,
        )

        assertEquals(
            "ALREADY_CONVERTED",
            duplicate.reason,
        )

        assertEquals(
            convertedLatitude,
            latitudes.single(),
            0.0f,
        )

        assertEquals(
            convertedLongitude,
            longitudes.single(),
            0.0f,
        )
    }

    @Test
    fun reusedArraysWithNewCoordinatesAreConvertedAgain() {
        val transformer = HeatmapCoordinateTransformer(
            converter = { latitude, longitude ->
                Coordinate(
                    latitude + 1.0,
                    longitude + 2.0,
                )
            },
        )

        val latitudes = floatArrayOf(22.543096f)

        val longitudes = floatArrayOf(114.057865f)

        val first = transformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 1,
        )

        assertEquals(
            HeatmapConversionOutcome.CONVERTED,
            first.outcome,
        )

        // 模拟 Google Photos 复用数组并填入下一批原始坐标。
        latitudes[0] = 23.129110f
        longitudes[0] = 113.264385f

        val reused = transformer.transform(
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = 1,
        )

        assertEquals(
            HeatmapConversionOutcome.CONVERTED,
            reused.outcome,
        )

        assertEquals(
            24.129110f,
            latitudes.single(),
            0.0001f,
        )

        assertEquals(
            115.264385f,
            longitudes.single(),
            0.0001f,
        )
    }

    @Test
    fun mismatchedArraysAreSkippedWithoutMutation() {
        val latitudes = floatArrayOf(22.5f, 31.2f)
        val longitudes = floatArrayOf(114.0f)
        val transformer = HeatmapCoordinateTransformer()

        val result = transformer.transform(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("ARRAY_SIZE_MISMATCH", result.reason)
        assertArrayEquals(floatArrayOf(22.5f, 31.2f), latitudes, 0.0f)
        assertArrayEquals(floatArrayOf(114.0f), longitudes, 0.0f)
    }

    @Test
    fun outsideChinaBatchRemainsUnchanged() {
        val latitudes = floatArrayOf(35.6762f)
        val longitudes = floatArrayOf(139.6503f)
        val transformer = HeatmapCoordinateTransformer()

        val result = transformer.transform(latitudes, longitudes, 1)

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
