package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import io.github.togls.hypertweaks.feature.googlephotos.policy.CoordinateTransformOutcome
import io.github.togls.hypertweaks.feature.googlephotos.policy.CoordinateTransformPolicy
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTarget
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTargetResolver
import io.github.togls.hypertweaks.feature.googlephotos.session.GooglePhotosMapSessionTracker
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal class GooglePhotosHeatmapIndexHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val engine = context.engine
    private val transformer = HeatmapCoordinateTransformer()
    private lateinit var addItemsMethod: Method

    fun install(resolver: GooglePhotosTargetResolver) {
        val builderClass = resolver.resolve(GooglePhotosTarget.S2_BUILDER).targetClass
        addItemsMethod = GooglePhotosHeatmapIndexMethodMatcher.find(builderClass)
            ?: error("S2 heatmap index add-items method is ambiguous or unavailable")
        resolver.bindingSelected(GooglePhotosTarget.S2_BUILDER, addItemsMethod.toGenericString())
        addItemsMethod.isAccessible = true
        engine.hook(addItemsMethod) { chain ->
            observeAndConvert(chain.thisObject, chain.args)
            chain.proceed()
        }
    }

    private fun observeAndConvert(receiver: Any?, arguments: List<Any?>) {
        val latitudes = arguments.getOrNull(LatitudeArgumentIndex) as? FloatArray
        val longitudes = arguments.getOrNull(LongitudeArgumentIndex) as? FloatArray
        val itemCount = arguments.getOrNull(ItemCountArgumentIndex) as? Int
        val session = sessionTracker.currentSession()?.toProbeLogSnapshot()
        val callCount = logger.heatmapInvoked(
            method = addItemsMethod.toGenericString(),
            receiverClass = receiver?.javaClass?.name,
            itemCount = itemCount,
            session = session,
        )
        // Session 只参与诊断；后台构建批次必须继续执行全局渲染坐标修正。
        val result = transformer.transform(latitudes, longitudes, itemCount)
        logger.heatmapResult(callCount, session, result)
    }

    private companion object {
        private const val LatitudeArgumentIndex = 1
        private const val LongitudeArgumentIndex = 2
        private const val ItemCountArgumentIndex = 4
    }
}

internal object GooglePhotosHeatmapIndexMethodMatcher {
    private val ExpectedParameterTypes = arrayOf(
        LongArray::class.java,
        FloatArray::class.java,
        FloatArray::class.java,
        LongArray::class.java,
        Int::class.javaPrimitiveType,
    )

    fun find(builderClass: Class<*>): Method? {
        return builderClass.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                Modifier.isSynchronized(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(ExpectedParameterTypes)
        }
    }
}

internal enum class HeatmapConversionOutcome {
    SKIPPED,
    CONVERTED,
    FAILED,
}

internal data class HeatmapBatchConversionResult(
    val convertedCount: Int,
    val validCount: Int = 0,
    val mainlandCount: Int = 0,
    val failure: Exception? = null,
    val failureReason: String? = null,
)

internal data class HeatmapConversionResult(
    val outcome: HeatmapConversionOutcome,
    val reason: String,
    val batchResult: HeatmapBatchConversionResult = HeatmapBatchConversionResult(0),
) {
    val failure: Exception? = batchResult.failure
}

internal class HeatmapCoordinateTransformer(
    converter: (Double, Double) -> Coordinate = ChinaCoordinateConverter::wgs84ToGcj02,
    private val conversionGuard: HeatmapBatchConversionGuard = HeatmapBatchConversionGuard(),
) {
    private val coordinatePolicy = CoordinateTransformPolicy(converter)

    fun transform(
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
    ): HeatmapConversionResult {
        val inspection = HeatmapCoordinateBatchTransformer.inspect(latitudes, longitudes, itemCount)
        if (inspection.failure != null) {
            return skipped(inspection.failureReason ?: "INVALID_BATCH", inspection)
        }
        if (latitudes == null || longitudes == null || itemCount == null) {
            return skipped("INVALID_ARGUMENTS", inspection)
        }
        if (conversionGuard.isAlreadyConverted(latitudes, longitudes, itemCount)) {
            return skipped("ALREADY_CONVERTED", inspection)
        }
        return convertBatch(latitudes, longitudes, itemCount)
    }

    private fun convertBatch(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): HeatmapConversionResult {
        val convertedLatitudes = latitudes.copyOf()
        val convertedLongitudes = longitudes.copyOf()
        val result = HeatmapCoordinateBatchTransformer.transform(
            convertedLatitudes,
            convertedLongitudes,
            itemCount,
            coordinatePolicy,
        )
        if (result.failure != null) {
            return HeatmapConversionResult(
                HeatmapConversionOutcome.FAILED,
                result.failureReason ?: "CONVERSION_FAILED",
                result,
            )
        }
        if (result.convertedCount == 0) return skipped("NO_MAINLAND_COORDINATES", result)
        convertedLatitudes.copyInto(latitudes, endIndex = itemCount)
        convertedLongitudes.copyInto(longitudes, endIndex = itemCount)
        conversionGuard.record(latitudes, longitudes, itemCount)
        return HeatmapConversionResult(HeatmapConversionOutcome.CONVERTED, "WGS84_TO_GCJ02", result)
    }

    private fun skipped(
        reason: String,
        batchResult: HeatmapBatchConversionResult,
    ): HeatmapConversionResult {
        return HeatmapConversionResult(HeatmapConversionOutcome.SKIPPED, reason, batchResult)
    }
}

internal data class HeatmapBatchConversionStamp(
    val longitudeArray: WeakReference<FloatArray>,
    val itemCount: Int,
    val convertedFingerprint: Long,
)

internal class HeatmapBatchConversionGuard {
    private val stamps = WeakHashMap<FloatArray, HeatmapBatchConversionStamp>()

    @Synchronized
    fun isAlreadyConverted(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): Boolean {
        val stamp = stamps[latitudes] ?: return false
        if (stamp.longitudeArray.get() !== longitudes || stamp.itemCount != itemCount) return false
        return stamp.convertedFingerprint == fingerprint(latitudes, longitudes, itemCount)
    }

    @Synchronized
    fun record(latitudes: FloatArray, longitudes: FloatArray, itemCount: Int) {
        stamps[latitudes] = HeatmapBatchConversionStamp(
            longitudeArray = WeakReference(longitudes),
            itemCount = itemCount,
            convertedFingerprint = fingerprint(latitudes, longitudes, itemCount),
        )
    }

    private fun fingerprint(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): Long {
        var hash = FingerprintSeed
        repeat(itemCount) { index ->
            hash = FingerprintMultiplier * hash + latitudes[index].toRawBits()
            hash = FingerprintMultiplier * hash + longitudes[index].toRawBits()
        }
        return hash
    }

    private companion object {
        private const val FingerprintSeed = 1125899906842597L
        private const val FingerprintMultiplier = 31L
    }
}

internal object HeatmapCoordinateBatchTransformer {
    fun inspect(
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
    ): HeatmapBatchConversionResult {
        if (latitudes == null || longitudes == null || itemCount == null) {
            return invalidBatch("INVALID_ARGUMENTS")
        }
        if (latitudes.size != longitudes.size) return invalidBatch("ARRAY_SIZE_MISMATCH")
        if (itemCount !in 0..latitudes.size) return invalidBatch("INVALID_BATCH_SIZE")
        var validCount = 0
        var mainlandCount = 0
        repeat(itemCount) { index ->
            val latitude = latitudes[index].toDouble()
            val longitude = longitudes[index].toDouble()
            if (CoordinateValidator.isValid(latitude, longitude)) validCount += 1
            if (CoordinateValidator.isInMainlandChina(latitude, longitude)) mainlandCount += 1
        }
        return HeatmapBatchConversionResult(0, validCount, mainlandCount)
    }

    fun transform(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
        policy: CoordinateTransformPolicy = CoordinateTransformPolicy(),
    ): HeatmapBatchConversionResult {
        val inspection = inspect(latitudes, longitudes, itemCount)
        if (inspection.failure != null) return inspection
        val convertedLatitudes = latitudes.copyOf()
        val convertedLongitudes = longitudes.copyOf()
        val attempt = convertCoordinates(
            latitudes,
            longitudes,
            convertedLatitudes,
            convertedLongitudes,
            itemCount,
            policy,
        )
        if (attempt.failure != null) return inspection.copy(
            failure = attempt.failure,
            failureReason = "CONVERSION_FAILED",
        )
        convertedLatitudes.copyInto(latitudes, endIndex = itemCount)
        convertedLongitudes.copyInto(longitudes, endIndex = itemCount)
        return inspection.copy(convertedCount = attempt.convertedCount)
    }

    private fun convertCoordinates(
        latitudes: FloatArray,
        longitudes: FloatArray,
        convertedLatitudes: FloatArray,
        convertedLongitudes: FloatArray,
        itemCount: Int,
        policy: CoordinateTransformPolicy,
    ): HeatmapCoordinateConversionAttempt {
        var convertedCount = 0
        repeat(itemCount) { index ->
            val original = Coordinate(latitudes[index].toDouble(), longitudes[index].toDouble())
            val result = policy.transform(original)
            if (result.outcome == CoordinateTransformOutcome.FAILED) {
                return HeatmapCoordinateConversionAttempt(0, result.failure)
            }
            if (result.outcome == CoordinateTransformOutcome.CONVERTED) {
                val converted = checkNotNull(result.converted)
                convertedLatitudes[index] = converted.latitude.toFloat()
                convertedLongitudes[index] = converted.longitude.toFloat()
                convertedCount += 1
            }
        }
        return HeatmapCoordinateConversionAttempt(convertedCount)
    }

    private fun invalidBatch(reason: String): HeatmapBatchConversionResult {
        return HeatmapBatchConversionResult(
            convertedCount = 0,
            failure = IllegalArgumentException(reason),
            failureReason = reason,
        )
    }
}

internal data class HeatmapCoordinateConversionAttempt(
    val convertedCount: Int,
    val failure: Exception? = null,
)
