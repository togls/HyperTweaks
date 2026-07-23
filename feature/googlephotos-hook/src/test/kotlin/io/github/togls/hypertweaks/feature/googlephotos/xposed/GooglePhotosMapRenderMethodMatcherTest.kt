package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePhotosMapRenderMethodMatcherTest {

    @Test
    fun findsMarkerApiThroughActivityControllerStructure() {
        val report =
            GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java).inspect(FakeMapActivity::class.java)
        val binding = report.binding

        assertNotNull(binding)
        assertEquals(1, report.controllerCandidateCount)
        assertEquals(1, report.facadeCandidateCount)
        assertEquals(1, report.bindings.size)
        assertEquals("controller", binding?.controllerField?.name)
        assertEquals("mapFacade", binding?.facadeField?.name)
        assertEquals("addMarker", binding?.method?.name)
        assertEquals("position", binding?.positionField?.name)
    }

    @Test
    fun failsClosedWhenMoreThanOneRenderMethodMatches() {
        val report = GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java).inspect(
                AmbiguousMapActivity::class.java
            )

        assertEquals(2, report.bindings.size)
        assertNull(report.binding)
    }

    @Test
    fun failsClosedWhenControllerDoesNotExposeMarkerApi() {
        val report = GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java).inspect(
                ActivityWithoutMap::class.java
            )

        assertEquals(0, report.bindings.size)
        assertNull(report.binding)
    }

    @Test
    fun markerConversionDoesNotRequireSessionAndAvoidsConvertedCoordinateDuplicate() {
        val transformer = MarkerCoordinateTransformer(
            converter = { latitude, longitude ->
                Coordinate(
                    latitude + 1.0,
                    longitude + 2.0,
                )
            },
        )

        val target = Any()
        var currentCoordinate = Shenzhen

        val converted = transformer.transform(
            target = target,
            original = currentCoordinate,
        ) {
            currentCoordinate = it
        }

        val duplicate = transformer.transform(
            target = target,
            original = currentCoordinate,
        ) {
            currentCoordinate = it
        }

        assertEquals(
            MarkerConversionOutcome.CONVERTED,
            converted.outcome,
        )

        assertEquals(
            Coordinate(
                23.543096,
                116.057865,
            ),
            currentCoordinate,
        )

        assertEquals(
            MarkerConversionOutcome.SKIPPED,
            duplicate.outcome,
        )

        assertEquals(
            "ALREADY_CONVERTED",
            duplicate.reason,
        )
    }

    @Test
    fun reusedMarkerTargetWithNewCoordinateIsConvertedAgain() {
        val transformer = MarkerCoordinateTransformer(
            converter = { latitude, longitude ->
                Coordinate(
                    latitude + 1.0,
                    longitude + 2.0,
                )
            },
        )

        val target = Any()
        var currentCoordinate = Shenzhen

        val first = transformer.transform(
            target = target,
            original = currentCoordinate,
        ) {
            currentCoordinate = it
        }

        assertEquals(
            MarkerConversionOutcome.CONVERTED,
            first.outcome,
        )

        /*
         * 模拟同一个 MarkerOptions 被 Google Photos 复用，
         * 并写入另一个未经转换的原始坐标。
         */
        currentCoordinate = Coordinate(
            23.129110,
            113.264385,
        )

        val reused = transformer.transform(
            target = target,
            original = currentCoordinate,
        ) {
            currentCoordinate = it
        }

        assertEquals(
            MarkerConversionOutcome.CONVERTED,
            reused.outcome,
        )

        assertEquals(
            Coordinate(
                24.129110,
                115.264385,
            ),
            currentCoordinate,
        )
    }

    @Test
    fun markerConversionLeavesHongKongOutsideAndInvalidCoordinatesUnchanged() {
        val transformer = MarkerCoordinateTransformer()

        val hongKong = transformer.transform(Any(), Coordinate(22.3193, 114.1694)) {}
        val outside = transformer.transform(Any(), Coordinate(35.6762, 139.6503)) {}
        val invalid = transformer.transform(Any(), Coordinate(Double.NaN, 114.0)) {}

        assertEquals(MarkerConversionOutcome.UNCHANGED, hongKong.outcome)
        assertEquals("OUTSIDE_CHINA", hongKong.reason)
        assertEquals(MarkerConversionOutcome.UNCHANGED, outside.outcome)
        assertEquals("OUTSIDE_CHINA", outside.reason)
        assertEquals(MarkerConversionOutcome.UNCHANGED, invalid.outcome)
        assertEquals("INVALID_COORDINATE", invalid.reason)
        assertEquals(outside.original, outside.converted)
        assertNull(invalid.failure)
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
        @Suppress("unused") private val controller = FakeMapController()
    }

    private class FakeMapController {
        @Suppress("unused") private val mapFacade = FakeMapFacade()
    }

    private class FakeMapFacade {
        @Suppress("UNUSED_PARAMETER")
        fun addMarker(options: FakeMarkerOptions): FakeMarker = FakeMarker()
    }

    private class FakeMarkerOptions {
        @Suppress("unused") var position: FakeCoordinate? = null
    }

    private class FakeMarker {
        fun position(): FakeCoordinate = FakeCoordinate(0.0, 0.0)
    }

    private class AmbiguousMapActivity {
        @Suppress("unused") private val controller = AmbiguousMapController()
    }

    private class AmbiguousMapController {
        @Suppress("unused") private val mapFacade = AmbiguousMapFacade()
    }

    private class AmbiguousMapFacade {
        @Suppress("UNUSED_PARAMETER")
        fun addMarker(options: FakeMarkerOptions): FakeMarker = FakeMarker()

        @Suppress("UNUSED_PARAMETER")
        fun updateMarker(options: FakeMarkerOptions): FakeMarker = FakeMarker()
    }

    private class ActivityWithoutMap {
        @Suppress("unused") private val controller = ControllerWithoutMap()
    }

    private class ControllerWithoutMap {
        @Suppress("unused") private val label = "no map"
    }

    private class FakeCoordinate(
        latitude: Double,
        longitude: Double,
    ) {
        private val longitudeStorage = longitude
        private val latitudeStorage = latitude
    }

    private companion object {
        val Shenzhen = Coordinate(22.543096, 114.057865)
    }
}
