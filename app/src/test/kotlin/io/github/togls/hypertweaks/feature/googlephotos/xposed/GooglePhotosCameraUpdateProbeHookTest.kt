package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePhotosCameraUpdateProbeHookTest {
    @Test
    fun resolvesOnlySingleCoordinateCameraUpdateMethod() {
        val methods = CameraUpdateBindingResolver(FakeCoordinate::class.java)
            .resolve(FakeCameraUpdateFactory::class.java)

        assertEquals(listOf("newLatLng"), methods.map(MethodName))
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
