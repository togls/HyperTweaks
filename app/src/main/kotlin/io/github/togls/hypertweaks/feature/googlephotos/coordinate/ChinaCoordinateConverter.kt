package io.github.togls.hypertweaks.feature.googlephotos.coordinate

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

internal data class CoordinateConversionResult(
    val coordinate: Coordinate,
    val failure: Exception? = null,
)

object ChinaCoordinateConverter {

    fun wgs84ToGcj02(
        latitude: Double,
        longitude: Double,
    ): Coordinate {
        return convertSafely(
            latitude = latitude,
            longitude = longitude,
            conversion = ::convertInsideMainlandChina,
        ).coordinate
    }

    internal fun wgs84ToGcj02Result(
        latitude: Double,
        longitude: Double,
    ): CoordinateConversionResult {
        return convertSafely(
            latitude = latitude,
            longitude = longitude,
            conversion = ::convertInsideMainlandChina,
        )
    }

    internal fun convertSafely(
        latitude: Double,
        longitude: Double,
        conversion: (Double, Double) -> Coordinate,
    ): CoordinateConversionResult {
        val original = Coordinate(latitude, longitude)
        if (!CoordinateValidator.isValid(latitude, longitude)) {
            return CoordinateConversionResult(original)
        }
        if (!CoordinateValidator.isInMainlandChina(latitude, longitude)) {
            return CoordinateConversionResult(original)
        }

        return try {
            CoordinateConversionResult(conversion(latitude, longitude))
        } catch (error: Exception) {
            CoordinateConversionResult(original, error)
        }
    }

    private fun convertInsideMainlandChina(
        latitude: Double,
        longitude: Double,
    ): Coordinate {
        val latitudeOffset = transformLatitude(
            longitude - ReferenceLongitude,
            latitude - ReferenceLatitude,
        )
        val longitudeOffset = transformLongitude(
            longitude - ReferenceLongitude,
            latitude - ReferenceLatitude,
        )
        val latitudeRadians = latitude / DegreesPerRadian * PI
        val magic = 1.0 - EccentricitySquared * sin(latitudeRadians).let { it * it }
        val squareRootMagic = sqrt(magic)

        val adjustedLatitude = latitude +
            latitudeOffset * DegreesPerRadian /
            ((SemiMajorAxis * (1.0 - EccentricitySquared)) / (magic * squareRootMagic) * PI)
        val adjustedLongitude = longitude +
            longitudeOffset * DegreesPerRadian /
            (SemiMajorAxis / squareRootMagic * kotlin.math.cos(latitudeRadians) * PI)
        return Coordinate(adjustedLatitude, adjustedLongitude)
    }

    private fun transformLatitude(
        longitudeDelta: Double,
        latitudeDelta: Double,
    ): Double {
        var offset = -100.0 +
            2.0 * longitudeDelta +
            3.0 * latitudeDelta +
            0.2 * latitudeDelta * latitudeDelta +
            0.1 * longitudeDelta * latitudeDelta +
            0.2 * sqrt(abs(longitudeDelta))
        offset += sineSeries(longitudeDelta)
        offset += latitudeSineSeries(latitudeDelta)
        return offset
    }

    private fun transformLongitude(
        longitudeDelta: Double,
        latitudeDelta: Double,
    ): Double {
        var offset = 300.0 +
            longitudeDelta +
            2.0 * latitudeDelta +
            0.1 * longitudeDelta * longitudeDelta +
            0.1 * longitudeDelta * latitudeDelta +
            0.1 * sqrt(abs(longitudeDelta))
        offset += sineSeries(longitudeDelta)
        offset += longitudeSineSeries(longitudeDelta)
        return offset
    }

    private fun sineSeries(value: Double): Double {
        return (20.0 * sin(6.0 * value * PI) +
            20.0 * sin(2.0 * value * PI)) * TwoThirds
    }

    private fun latitudeSineSeries(latitudeDelta: Double): Double {
        return (20.0 * sin(latitudeDelta * PI) +
            40.0 * sin(latitudeDelta / 3.0 * PI)) * TwoThirds +
            (160.0 * sin(latitudeDelta / 12.0 * PI) +
                320.0 * sin(latitudeDelta * PI / 30.0)) * TwoThirds
    }

    private fun longitudeSineSeries(longitudeDelta: Double): Double {
        return (20.0 * sin(longitudeDelta * PI) +
            40.0 * sin(longitudeDelta / 3.0 * PI)) * TwoThirds +
            (150.0 * sin(longitudeDelta / 12.0 * PI) +
                300.0 * sin(longitudeDelta / 30.0 * PI)) * TwoThirds
    }

    private const val SemiMajorAxis = 6_378_245.0
    private const val EccentricitySquared = 0.006693421622965943
    private const val ReferenceLatitude = 35.0
    private const val ReferenceLongitude = 105.0
    private const val DegreesPerRadian = 180.0
    private const val TwoThirds = 2.0 / 3.0
}
