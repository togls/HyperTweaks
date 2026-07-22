package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GooglePhotosS2QueryHookTest {
    @Test
    fun readsNativeQueryBoundsFromArguments() {
        val arguments = listOf(
            Any(),
            9L,
            19.70f,
            109.98f,
            19.80f,
            110.08f,
        )

        val bounds = S2QueryBoundsReader.read(arguments)

        assertEquals(19.70, bounds?.minimumLatitude ?: 0.0, 0.0001)
        assertEquals(109.98, bounds?.minimumLongitude ?: 0.0, 0.0001)
        assertEquals(19.80, bounds?.maximumLatitude ?: 0.0, 0.0001)
        assertEquals(110.08, bounds?.maximumLongitude ?: 0.0, 0.0001)
    }

    @Test
    fun rejectsIncompleteNativeQueryArguments() {
        assertNull(S2QueryBoundsReader.read(listOf(Any(), 9L, 19.70f)))
    }

    @Test
    fun calculatesConvertedBoundsWithoutMutatingQuery() {
        val transformer = S2QueryBoundsTransformer { latitude, longitude ->
            Coordinate(latitude + 1.0, longitude + 2.0)
        }
        val original = S2QueryBounds(19.70, 109.98, 19.80, 110.08)

        val converted = transformer.transform(original)

        assertEquals(S2QueryBounds(20.70, 111.98, 20.80, 112.08), converted)
        assertEquals(S2QueryBounds(19.70, 109.98, 19.80, 110.08), original)
    }

    @Test
    fun convertsMapQueryBoundsBackToPhotoDataCoordinates() {
        val transformer = S2QueryBoundsTransformer()
        val dataMinimum = Coordinate(19.732610, 110.007320)
        val dataMaximum = Coordinate(19.736255, 110.011570)
        val mapMinimum = ChinaCoordinateConverter.wgs84ToGcj02(
            dataMinimum.latitude,
            dataMinimum.longitude,
        )
        val mapMaximum = ChinaCoordinateConverter.wgs84ToGcj02(
            dataMaximum.latitude,
            dataMaximum.longitude,
        )
        val mapBounds = S2QueryBounds(
            minimumLatitude = mapMinimum.latitude,
            minimumLongitude = mapMinimum.longitude,
            maximumLatitude = mapMaximum.latitude,
            maximumLongitude = mapMaximum.longitude,
        )

        val dataBounds = transformer.transform(mapBounds)

        assertEquals(dataMinimum.latitude, dataBounds?.minimumLatitude ?: 0.0, 0.000001)
        assertEquals(dataMinimum.longitude, dataBounds?.minimumLongitude ?: 0.0, 0.000001)
        assertEquals(dataMaximum.latitude, dataBounds?.maximumLatitude ?: 0.0, 0.000001)
        assertEquals(dataMaximum.longitude, dataBounds?.maximumLongitude ?: 0.0, 0.000001)
    }

    @Test
    fun convertsBoundsOnlyForAnActiveMapSession() {
        val policy = S2QueryCoordinatePolicy(
            transformer = S2QueryBoundsTransformer { latitude, longitude ->
                Coordinate(latitude + 1.0, longitude + 2.0)
            },
        )
        val original = S2QueryBounds(19.70, 109.98, 19.80, 110.08)

        assertEquals(S2QueryBounds(20.70, 111.98, 20.80, 112.08), policy.transform(original, true))
        assertNull(policy.transform(original, false))
    }

    @Test
    fun writesConvertedBoundsWithoutReplacingUnrelatedArguments() {
        val index = Any()
        val tags = longArrayOf(1L)
        val arguments = arrayOf<Any?>(index, 9L, 19.70f, 109.98f, 19.80f, 110.08f, tags)
        val converted = S2QueryBounds(19.71, 109.97, 19.81, 110.07)

        val updated = S2QueryBoundsWriter.write(arguments.toList(), converted)

        assertSame(index, updated[0])
        assertEquals(9L, updated[1])
        assertEquals(19.71f, updated[2])
        assertEquals(109.97f, updated[3])
        assertEquals(19.81f, updated[4])
        assertEquals(110.07f, updated[5])
        assertSame(tags, updated[6])
        assertEquals(19.70f, arguments[2])
    }
}
