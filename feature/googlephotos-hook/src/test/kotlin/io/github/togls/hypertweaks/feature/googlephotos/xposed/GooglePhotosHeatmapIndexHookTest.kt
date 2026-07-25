package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.policy.CoordinateTransformPolicy
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
        assertNull(GooglePhotosHeatmapIndexMethodMatcher.find(UnsafeS2IndexBuilder::class.java))
    }

    @Test
    fun batchConversionIsGlobalAndDoesNotRequireSession() {
        val latitudes = floatArrayOf(22.543096f)
        val longitudes = floatArrayOf(114.057865f)
        val transformer = transformer()

        val result = transformer.transform(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.CONVERTED, result.outcome)
        assertEquals("WGS84_TO_GCJ02", result.reason)
        assertArrayEquals(floatArrayOf(23.543096f), latitudes, 0.0001f)
        assertArrayEquals(floatArrayOf(116.057865f), longitudes, 0.0001f)
    }

    @Test
    fun appliesSharedRegionPolicyAndOnlyMutatesPopulatedItems() {
        val latitudes = floatArrayOf(22.543096f, 22.3193f, 22.1987f, 25.0330f, 88.0f)
        val longitudes = floatArrayOf(114.057865f, 114.1694f, 113.5439f, 121.5654f, 99.0f)

        val result = transformer().transform(latitudes, longitudes, 4)

        assertEquals(HeatmapConversionOutcome.CONVERTED, result.outcome)
        assertEquals(1, result.batchResult.convertedCount)
        assertEquals(1, result.batchResult.mainlandCount)
        assertArrayEquals(
            floatArrayOf(23.543096f, 22.3193f, 22.1987f, 25.0330f, 88.0f),
            latitudes,
            0.0001f,
        )
    }

    @Test
    fun failedConversionLeavesWholeBatchUntouched() {
        val originalLatitudes = floatArrayOf(22.543096f, 31.2304f)
        val originalLongitudes = floatArrayOf(114.057865f, 121.4737f)
        val latitudes = originalLatitudes.copyOf()
        val longitudes = originalLongitudes.copyOf()
        val policy = CoordinateTransformPolicy { latitude, longitude ->
            if (latitude > 30.0) error("conversion failed")
            Coordinate(latitude + 1.0, longitude + 2.0)
        }

        val result = HeatmapCoordinateBatchTransformer.transform(
            latitudes,
            longitudes,
            2,
            policy,
        )

        assertNotNull(result.failure)
        assertArrayEquals(originalLatitudes, latitudes, 0.0f)
        assertArrayEquals(originalLongitudes, longitudes, 0.0f)
    }

    @Test
    fun convertedBatchIsNotConvertedTwiceButReusedArraysAreConverted() {
        val latitudes = floatArrayOf(22.543096f)
        val longitudes = floatArrayOf(114.057865f)
        val transformer = transformer()

        val first = transformer.transform(latitudes, longitudes, 1)
        val duplicate = transformer.transform(latitudes, longitudes, 1)
        latitudes[0] = 23.129110f
        longitudes[0] = 113.264385f
        val reused = transformer.transform(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.CONVERTED, first.outcome)
        assertEquals(HeatmapConversionOutcome.SKIPPED, duplicate.outcome)
        assertEquals("ALREADY_CONVERTED", duplicate.reason)
        assertEquals(HeatmapConversionOutcome.CONVERTED, reused.outcome)
        assertEquals(24.129110f, latitudes.single(), 0.0001f)
    }

    @Test
    fun invalidBatchIsSkippedWithoutMutation() {
        val latitudes = floatArrayOf(22.543096f, 31.2304f)
        val longitudes = floatArrayOf(114.057865f)

        val result = transformer().transform(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("ARRAY_SIZE_MISMATCH", result.reason)
        assertEquals(22.543096f, latitudes.first(), 0.0f)
    }

    private fun transformer(): HeatmapCoordinateTransformer {
        return HeatmapCoordinateTransformer(
            converter = { latitude, longitude ->
                Coordinate(latitude + 1.0, longitude + 2.0)
            },
        )
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
