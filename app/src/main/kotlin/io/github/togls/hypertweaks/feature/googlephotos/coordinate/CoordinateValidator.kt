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
            longitude in MainlandMinimumLongitude..MainlandMaximumLongitude &&
            !isInHongKong(latitude, longitude)
    }

    private fun isInHongKong(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        if (latitude !in HongKongMinimumLatitude..HongKongMaximumLatitude) return false
        if (longitude !in HongKongMinimumLongitude..HongKongMaximumLongitude) return false

        for (index in 0 until HongKongNorthernBoundary.lastIndex) {
            val westernPoint = HongKongNorthernBoundary[index]
            val easternPoint = HongKongNorthernBoundary[index + 1]
            if (longitude !in westernPoint.longitude..easternPoint.longitude) continue
            val segmentProgress = (longitude - westernPoint.longitude) /
                (easternPoint.longitude - westernPoint.longitude)
            val boundaryLatitude = westernPoint.latitude +
                segmentProgress * (easternPoint.latitude - westernPoint.latitude)
            return latitude <= boundaryLatitude + HongKongBoundaryPadding
        }
        return false
    }

    private const val MinimumLatitude = -90.0
    private const val MaximumLatitude = 90.0
    private const val MinimumLongitude = -180.0
    private const val MaximumLongitude = 180.0

    private const val MainlandMinimumLatitude = 0.8293
    private const val MainlandMaximumLatitude = 55.8271
    private const val MainlandMinimumLongitude = 72.004
    private const val MainlandMaximumLongitude = 137.8347

    private const val HongKongMinimumLatitude = 22.10
    private const val HongKongMaximumLatitude = 22.58
    private const val HongKongMinimumLongitude = 113.82
    private const val HongKongMaximumLongitude = 114.50
    private const val HongKongBoundaryPadding = 0.002

    /*
     * 香港北界并非矩形；仅用包围盒会把深圳南部误排除。
     * 折线取自香港民政事务总署行政区分界数据的北侧包络，少量余量用于覆盖折线简化误差。
     */
    private val HongKongNorthernBoundary = listOf(
        BoundaryPoint(113.82, 22.223968),
        BoundaryPoint(113.85, 22.280355),
        BoundaryPoint(113.88, 22.434833),
        BoundaryPoint(113.90, 22.445901),
        BoundaryPoint(113.93, 22.462504),
        BoundaryPoint(113.96, 22.482089),
        BoundaryPoint(114.00, 22.509082),
        BoundaryPoint(114.04, 22.505210),
        BoundaryPoint(114.08, 22.530837),
        BoundaryPoint(114.12, 22.534561),
        BoundaryPoint(114.16, 22.560933),
        BoundaryPoint(114.20, 22.557312),
        BoundaryPoint(114.24, 22.551990),
        BoundaryPoint(114.28, 22.565641),
        BoundaryPoint(114.32, 22.567429),
        BoundaryPoint(114.36, 22.566638),
        BoundaryPoint(114.40, 22.564131),
        BoundaryPoint(114.44, 22.557173),
        BoundaryPoint(114.48, 22.414079),
        BoundaryPoint(114.50, 22.370511),
    )
}

private data class BoundaryPoint(
    val longitude: Double,
    val latitude: Double,
)
