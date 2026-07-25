package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosCameraUpdateProbeHookTest {
    @Test
    fun resolvesCoordinateCameraUpdateMethodsWithOptionalZoom() {
        val methods = CameraUpdateBindingResolver(FakeCoordinate::class.java)
            .resolve(FakeCameraUpdateFactory::class.java)

        assertEquals(
            listOf("newLatLng", "newLatLngZoom"),
            methods.map(MethodName).sorted(),
        )
    }

    @Test
    fun convertsCoordinateOnlyWhileMapSessionIsActive() {
        val policy = CameraUpdateCoordinatePolicy()
        val coordinate = Coordinate(19.732610, 110.007320)

        val inactiveResult = policy.transform(coordinate, sessionActive = false)
        val activeResult = policy.transform(coordinate, sessionActive = true)

        assertNull(inactiveResult)
        assertEquals(LocationCoordinateOutcome.CONVERTED, activeResult?.outcome)
    }

    @Test
    fun currentLocationCameraTargetIsPassedThroughOnlyOnce() {
        var elapsedRealtime = 1_000L
        val scope = CurrentLocationCameraUpdateScope(
            clock = { elapsedRealtime },
            timeoutMillis = 1_000L,
        )
        val coordinate = Coordinate(22.543096, 114.057865)
        scope.record(sessionId = 7L, coordinate)

        val firstMatch = scope.consumeIfMatches(sessionId = 7L, coordinate)
        val secondMatch = scope.consumeIfMatches(sessionId = 7L, coordinate)

        assertTrue(firstMatch)
        assertFalse(secondMatch)
    }

    @Test
    fun currentLocationCameraTargetRejectsOtherSessionCoordinateAndExpiredMatch() {
        var elapsedRealtime = 1_000L
        val scope = CurrentLocationCameraUpdateScope(
            clock = { elapsedRealtime },
            timeoutMillis = 100L,
        )
        val coordinate = Coordinate(22.543096, 114.057865)
        scope.record(sessionId = 7L, coordinate)

        assertFalse(scope.consumeIfMatches(8L, coordinate))
        assertFalse(scope.consumeIfMatches(7L, Coordinate(22.543196, 114.057865)))
        elapsedRealtime += 101L
        assertFalse(scope.consumeIfMatches(7L, coordinate))
    }

    @Test
    fun currentLocationCameraPolicyPassesThroughInsteadOfConverting() {
        val policy = CameraUpdateCoordinatePolicy()
        val coordinate = Coordinate(22.543096, 114.057865)

        val result = policy.transform(
            original = coordinate,
            sessionActive = true,
            currentLocationTarget = true,
        )

        assertEquals(LocationCoordinateOutcome.UNCHANGED, result?.outcome)
        assertEquals("CURRENT_LOCATION_CAMERA_PASSTHROUGH", result?.reason)
        assertEquals(coordinate, result?.converted)
    }

    private class FakeCameraUpdateFactory {
        companion object {
            @JvmStatic
            fun newLatLng(@Suppress("UNUSED_PARAMETER") coordinate: FakeCoordinate): FakeCameraUpdate =
                FakeCameraUpdate()

            @JvmStatic
            fun newLatLngZoom(
                @Suppress("UNUSED_PARAMETER") coordinate: FakeCoordinate,
                @Suppress("UNUSED_PARAMETER") zoom: Float,
            ): FakeCameraUpdate = FakeCameraUpdate()

            @JvmStatic
            fun unrelated(@Suppress("UNUSED_PARAMETER") coordinate: FakeCoordinate, zoom: Int): Int = zoom
        }
    }

    private class FakeCoordinate
    private class FakeCameraUpdate

    private companion object {
        val MethodName: (java.lang.reflect.Method) -> String = java.lang.reflect.Method::getName
    }
}
