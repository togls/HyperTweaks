package io.github.togls.hypertweaks.feature.googlephotos.xposed

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
    fun s2IndexBatchRemainsInOriginalWgs84Coordinates() {
        val latitudes = floatArrayOf(22.543096f)
        val longitudes = floatArrayOf(114.057865f)
        val observer = HeatmapCoordinateObserver()

        val result = observer.observe(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("S2_INDEX_WGS84_PRESERVED", result.reason)
        assertArrayEquals(floatArrayOf(22.543096f), latitudes, 0.0f)
        assertArrayEquals(floatArrayOf(114.057865f), longitudes, 0.0f)
    }

    @Test
    fun observerReportsPopulatedBatchWithoutMutation() {
        val latitudes = floatArrayOf(22.543096f, 22.3193f, 22.1987f, 25.0330f, 88.0f)
        val longitudes = floatArrayOf(114.057865f, 114.1694f, 113.5439f, 121.5654f, 99.0f)
        val originalLatitudes = latitudes.copyOf()
        val originalLongitudes = longitudes.copyOf()

        val result = HeatmapCoordinateObserver().observe(latitudes, longitudes, 4)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals(0, result.batchResult.convertedCount)
        assertEquals(1, result.batchResult.mainlandCount)
        assertArrayEquals(originalLatitudes, latitudes, 0.0f)
        assertArrayEquals(originalLongitudes, longitudes, 0.0f)
    }

    @Test
    fun invalidBatchIsSkippedWithoutMutation() {
        val latitudes = floatArrayOf(22.543096f, 31.2304f)
        val longitudes = floatArrayOf(114.057865f)

        val result = HeatmapCoordinateObserver().observe(latitudes, longitudes, 1)

        assertEquals(HeatmapConversionOutcome.SKIPPED, result.outcome)
        assertEquals("ARRAY_SIZE_MISMATCH", result.reason)
        assertEquals(22.543096f, latitudes.first(), 0.0f)
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
