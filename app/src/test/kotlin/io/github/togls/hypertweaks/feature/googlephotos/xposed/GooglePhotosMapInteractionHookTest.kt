package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GooglePhotosMapInteractionHookTest {
    @Test
    fun resolvesCurrentLocationRequestThroughSharedMapFacade() {
        val activity = FakeMapActivity()
        val renderBinding = renderBinding(FakeMapActivity::class.java)
        val controller = requireNotNull(renderBinding.controllerField.get(activity))
        val report = CurrentLocationRequestResolver().inspect(controller, renderBinding.facadeField)

        assertNotNull(report.binding)
        assertEquals(1, report.controllerCandidateCount)
        assertEquals(1, report.methodCandidateCount)
        assertEquals("requestCurrentLocation", report.binding?.method?.name)
        assertSame((controller as FakeMapController).currentLocationController, report.binding?.receiver)
    }

    @Test
    fun resolvesCurrentLocationRequestBeforeMapFacadeValueIsAssigned() {
        val activity = FakeMapActivity()
        val renderBinding = renderBinding(FakeMapActivity::class.java)
        val controller = requireNotNull(renderBinding.controllerField.get(activity)) as FakeMapController
        controller.currentLocationController.mapFacade = null

        val report = CurrentLocationRequestResolver().inspect(controller, renderBinding.facadeField)

        assertNotNull(report.binding)
        assertEquals("requestCurrentLocation", report.binding?.method?.name)
    }

    @Test
    fun currentLocationResolverFailsClosedForAmbiguousRequestMethods() {
        val activity = AmbiguousLocationActivity()
        val renderBinding = renderBinding(AmbiguousLocationActivity::class.java)
        val controller = requireNotNull(renderBinding.controllerField.get(activity))
        val report = CurrentLocationRequestResolver().inspect(controller, renderBinding.facadeField)

        assertEquals(1, report.controllerCandidateCount)
        assertEquals(2, report.methodCandidateCount)
        assertNull(report.binding)
    }

    @Test
    fun readScopeCoversRequestPairAndMapsLayerButRejectsExpiredRequest() {
        var elapsedRealtime = 1_000L
        val scope = MapLocationReadScope(clock = { elapsedRealtime }, requestTimeoutMillis = 100L)
        val requestLocation = Any()
        scope.arm(sessionId = 7L)

        val firstRead = scope.decide(requestLocation, 7L, listOf("obfuscated.LocationCallback"))
        val pairedRead = scope.decide(requestLocation, 7L, listOf("obfuscated.LocationCallback"))
        val mapLayerRead = scope.decide(Any(), 7L, listOf("com.google.maps.internal.LocationLayer"))
        scope.arm(sessionId = 7L)
        elapsedRealtime += 101L
        val expiredRead = scope.decide(Any(), 7L, listOf("obfuscated.LocationCallback"))

        assertEquals(MapLocationReadSource.CURRENT_LOCATION_REQUEST, firstRead?.source)
        assertEquals(MapLocationReadSource.CURRENT_LOCATION_REQUEST, pairedRead?.source)
        assertEquals(MapLocationReadSource.MAPS_LOCATION_LAYER, mapLayerRead?.source)
        assertNull(expiredRead)
    }

    @Test
    fun locationTransformerConvertsMainlandAndLeavesHongKongAndOutsideUnchanged() {
        val transformer = LocationCoordinateTransformer { latitude, longitude ->
            Coordinate(latitude + 1.0, longitude + 2.0)
        }

        val converted = transformer.transform(Coordinate(22.543096, 114.057865))
        val hongKong = transformer.transform(Coordinate(22.3193, 114.1694))
        val outside = transformer.transform(Coordinate(35.6762, 139.6503))

        assertEquals(LocationCoordinateOutcome.CONVERTED, converted.outcome)
        assertEquals(Coordinate(23.543096, 116.057865), converted.converted)
        assertEquals(LocationCoordinateOutcome.UNCHANGED, hongKong.outcome)
        assertEquals(hongKong.original, hongKong.converted)
        assertEquals(LocationCoordinateOutcome.UNCHANGED, outside.outcome)
        assertEquals(outside.original, outside.converted)
    }

    private fun renderBinding(activityClass: Class<*>): MapRenderBinding {
        return GooglePhotosMapRenderMethodMatcher(FakeCoordinate::class.java)
            .inspect(activityClass)
            .binding ?: error("Expected one render binding")
    }

    private class FakeMapActivity {
        private val controller = FakeMapController()
    }

    private class FakeMapController {
        private val mapFacade = FakeMapFacade()
        val currentLocationController = FakeCurrentLocationController(mapFacade)
    }

    private class AmbiguousLocationActivity {
        private val controller = AmbiguousLocationController()
    }

    private class AmbiguousLocationController {
        private val mapFacade = FakeMapFacade()
        private val currentLocationController = AmbiguousCurrentLocationController(mapFacade)
    }

    private class FakeMapFacade {
        fun addMarker(@Suppress("UNUSED_PARAMETER") options: FakeMarkerOptions): FakeMarker = FakeMarker()
    }

    private class FakeMarkerOptions {
        private var position: FakeCoordinate? = null
    }

    private class FakeMarker {
        fun position(): FakeCoordinate = FakeCoordinate(0.0, 0.0)
    }

    private class FakeCurrentLocationController(var mapFacade: FakeMapFacade?) {
        fun requestCurrentLocation() = Unit
    }

    private class AmbiguousCurrentLocationController(private val mapFacade: FakeMapFacade) {
        fun requestCurrentLocation() = Unit
        fun retryCurrentLocation() = Unit
    }

    private class FakeCoordinate(latitude: Double, longitude: Double) {
        private val latitudeValue = latitude
        private val longitudeValue = longitude
    }
}
