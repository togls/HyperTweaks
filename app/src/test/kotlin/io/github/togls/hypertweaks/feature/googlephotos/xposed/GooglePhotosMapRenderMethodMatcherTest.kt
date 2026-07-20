package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePhotosMapRenderMethodMatcherTest {

    @Test
    fun findsMarkerApiThroughActivityControllerStructure() {
        val binding = GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java)
            .find(FakeMapActivity::class.java)

        assertNotNull(binding)
        assertEquals("addMarker", binding?.method?.name)
        assertEquals("position", binding?.positionField?.name)
    }

    @Test
    fun failsClosedWhenMoreThanOneRenderMethodMatches() {
        val binding = GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java)
            .find(AmbiguousMapActivity::class.java)

        assertNull(binding)
    }

    @Test
    fun failsClosedWhenControllerDoesNotExposeMarkerApi() {
        val binding = GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java)
            .find(ActivityWithoutMap::class.java)

        assertNull(binding)
    }

    @Test
    fun coordinateAccessorResolvesFieldsByConstructorValues() {
        val accessors = CoordinateAccessorResolver.resolve(FakeCoordinate::class.java)
        val original = Coordinate(22.543096, 114.057865)

        assertNotNull(accessors)
        val created = accessors!!.create(original)
        assertEquals(original, accessors.read(created))
    }

    private class FakeMapActivity {
        @Suppress("unused")
        private val controller = FakeMapController()
    }

    private class FakeMapController {
        @Suppress("unused")
        private val mapFacade = FakeMapFacade()
    }

    private class FakeMapFacade {
        @Suppress("UNUSED_PARAMETER")
        fun addMarker(options: FakeMarkerOptions): FakeMarker = FakeMarker()
    }

    private class FakeMarkerOptions {
        @Suppress("unused")
        var position: FakeCoordinate? = null
    }

    private class FakeMarker {
        fun position(): FakeCoordinate = FakeCoordinate(0.0, 0.0)
    }

    private class AmbiguousMapActivity {
        @Suppress("unused")
        private val controller = AmbiguousMapController()
    }

    private class AmbiguousMapController {
        @Suppress("unused")
        private val mapFacade = AmbiguousMapFacade()
    }

    private class AmbiguousMapFacade {
        @Suppress("UNUSED_PARAMETER")
        fun addMarker(options: FakeMarkerOptions): FakeMarker = FakeMarker()

        @Suppress("UNUSED_PARAMETER")
        fun updateMarker(options: FakeMarkerOptions): FakeMarker = FakeMarker()
    }

    private class ActivityWithoutMap {
        @Suppress("unused")
        private val controller = ControllerWithoutMap()
    }

    private class ControllerWithoutMap {
        @Suppress("unused")
        private val label = "no map"
    }

    private class FakeCoordinate(
        latitude: Double,
        longitude: Double,
    ) {
        private val longitudeStorage = longitude
        private val latitudeStorage = latitude
    }
}
