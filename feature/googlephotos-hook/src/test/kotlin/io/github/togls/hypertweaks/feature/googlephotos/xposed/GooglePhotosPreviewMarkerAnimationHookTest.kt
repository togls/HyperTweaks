package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosPreviewMarkerAnimationHookTest {
    @Test
    fun resolvesOnlyGenericFourArgumentConstructor() {
        val constructors = PreviewMarkerAnimationBindingResolver()
            .resolve(FakeAnimationListener::class.java)

        assertEquals(1, constructors.size)
        assertEquals(4, constructors.single().parameterCount)
    }

    @Test
    fun recognizesOnlyLatLngToLatLngMarkerAnimation() {
        val start = FakeCoordinate()
        val target = FakeCoordinate()
        val marker = FakeMarker()

        assertTrue(
            PreviewMarkerAnimationArguments.matches(
                listOf(start, target, marker, 1),
                FakeCoordinate::class.java,
                FakeMarker::class.java,
            ),
        )
        assertFalse(
            PreviewMarkerAnimationArguments.matches(
                listOf(start, target, Any(), 1),
                FakeCoordinate::class.java,
                FakeMarker::class.java,
            ),
        )
    }

    @Test
    fun replacesOnlyAnimationTargetCoordinate() {
        val start = FakeCoordinate()
        val target = FakeCoordinate()
        val marker = FakeMarker()
        val converted = FakeCoordinate()
        val original = listOf(start, target, marker, 1)

        val updated = PreviewMarkerAnimationArguments.withTarget(original, converted)

        assertSame(start, updated[0])
        assertSame(converted, updated[1])
        assertSame(marker, updated[2])
        assertEquals(1, updated[3])
        assertSame(target, original[1])
    }

    @Test
    fun convertsTargetOnlyWhileMapSessionIsActive() {
        val policy = PreviewMarkerAnimationCoordinatePolicy()
        val target = Coordinate(19.732610, 110.007320)

        val inactiveResult = policy.transform(target, sessionActive = false)
        val activeResult = policy.transform(target, sessionActive = true)

        assertNull(inactiveResult)
        assertEquals(LocationCoordinateOutcome.CONVERTED, activeResult?.outcome)
    }

    private class FakeAnimationListener(
        @Suppress("UNUSED_PARAMETER") first: Any,
        @Suppress("UNUSED_PARAMETER") second: Any,
        @Suppress("UNUSED_PARAMETER") third: Any,
        @Suppress("UNUSED_PARAMETER") variant: Int,
    ) {
        @Suppress("UNUSED_PARAMETER")
        constructor(first: Any, second: Any) : this(first, second, Any(), 0)
    }

    private class FakeCoordinate
    private class FakeMarker
}
