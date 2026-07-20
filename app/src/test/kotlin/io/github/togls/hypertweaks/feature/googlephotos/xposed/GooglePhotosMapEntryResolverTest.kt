package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePhotosMapEntryResolverTest {

    @Test
    fun collectionsEntryInsideWindowResolvesToCollections() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.COLLECTIONS, resolver.consumeMapEntrySource(now = 5_100L))
    }

    @Test
    fun nonCollectionsEntryInsideWindowResolvesToOther() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.HomeActivity, now = 100L)

        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 200L))
    }

    @Test
    fun expiredEntryFailsClosedAndIsConsumed() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 5_101L))
        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 5_101L))
    }

    @Test
    fun backwardElapsedTimeFailsClosed() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 99L))
    }

    @Test
    fun markerCanOnlyBeConsumedOnce() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)

        assertEquals(MapEntrySource.COLLECTIONS, resolver.consumeMapEntrySource(now = 101L))
        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 102L))
    }

    @Test
    fun latestNavigationReplacesStaleCollectionsMarker() {
        val resolver = GooglePhotosMapEntryResolver()

        resolver.recordNavigation(GooglePhotosClassNames.CollectionsActivity, now = 100L)
        resolver.recordNavigation(GooglePhotosClassNames.HomeActivity, now = 101L)

        assertEquals(MapEntrySource.OTHER, resolver.consumeMapEntrySource(now = 102L))
    }
}
