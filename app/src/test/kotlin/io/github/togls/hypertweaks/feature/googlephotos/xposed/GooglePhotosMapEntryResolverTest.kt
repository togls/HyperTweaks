package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePhotosMapEntryResolverTest {

    @Test
    fun collectionsEntryInsideWindowResolvesToCollections() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.COLLECTIONS, resolver.consumeMapEntrySource(now = 3_100L))
    }

    @Test
    fun nonCollectionsEntryInsideWindowResolvesToOther() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.HomeActivity, now = 100L)

        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 200L))
    }

    @Test
    fun expiredEntryResolvesToUnknownAndIsConsumed() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.UNKNOWN, resolver.consumeMapEntrySource(now = 3_101L))
        assertEquals(MapEntrySource.UNKNOWN, resolver.consumeMapEntrySource(now = 3_101L))
    }

    @Test
    fun backwardElapsedTimeResolvesToUnknown() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.UNKNOWN, resolver.consumeMapEntrySource(now = 99L))
    }
}
