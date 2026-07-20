package io.github.togls.hypertweaks.feature.googlephotos.coordinate

object CoordinateValidator {

    fun isValid(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        return latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in MinimumLatitude..MaximumLatitude &&
            longitude in MinimumLongitude..MaximumLongitude
    }

    fun isInMainlandChina(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        return latitude in MainlandMinimumLatitude..MainlandMaximumLatitude &&
            longitude in MainlandMinimumLongitude..MainlandMaximumLongitude
    }

    private const val MinimumLatitude = -90.0
    private const val MaximumLatitude = 90.0
    private const val MinimumLongitude = -180.0
    private const val MaximumLongitude = 180.0

    private const val MainlandMinimumLatitude = 0.8293
    private const val MainlandMaximumLatitude = 55.8271
    private const val MainlandMinimumLongitude = 72.004
    private const val MainlandMaximumLongitude = 137.8347
}
