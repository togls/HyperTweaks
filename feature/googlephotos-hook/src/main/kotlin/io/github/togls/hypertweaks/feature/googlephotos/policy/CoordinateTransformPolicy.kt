package io.github.togls.hypertweaks.feature.googlephotos.policy

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator

internal enum class CoordinateTransformOutcome {
    CONVERTED,
    UNCHANGED,
    FAILED,
}

internal data class CoordinateTransformResult(
    val outcome: CoordinateTransformOutcome,
    val reason: String,
    val original: Coordinate,
    val converted: Coordinate? = null,
    val failure: Exception? = null,
)

/**
 * Marker、热力图和定位展示共用这一策略，避免不同入口形成不同的区域边界。
 */
internal class CoordinateTransformPolicy(
    private val converter: (Double, Double) -> Coordinate =
        ChinaCoordinateConverter::wgs84ToGcj02,
) {
    fun transform(original: Coordinate): CoordinateTransformResult {
        if (!CoordinateValidator.isValid(original.latitude, original.longitude)) {
            return unchanged("INVALID_COORDINATE", original)
        }
        if (!CoordinateValidator.isInMainlandChina(original.latitude, original.longitude)) {
            return unchanged("OUTSIDE_CHINA", original)
        }
        return convert(original)
    }

    private fun convert(original: Coordinate): CoordinateTransformResult {
        return try {
            val converted = converter(original.latitude, original.longitude)
            if (converted == original) {
                unchanged("NO_OFFSET", original)
            } else {
                CoordinateTransformResult(
                    outcome = CoordinateTransformOutcome.CONVERTED,
                    reason = "WGS84_TO_GCJ02",
                    original = original,
                    converted = converted,
                )
            }
        } catch (error: Exception) {
            CoordinateTransformResult(
                outcome = CoordinateTransformOutcome.FAILED,
                reason = "CONVERSION_FAILED",
                original = original,
                failure = error,
            )
        }
    }

    private fun unchanged(reason: String, original: Coordinate): CoordinateTransformResult {
        return CoordinateTransformResult(
            outcome = CoordinateTransformOutcome.UNCHANGED,
            reason = reason,
            original = original,
            converted = original,
        )
    }
}
