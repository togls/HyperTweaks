package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.os.SystemClock
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

internal class GooglePhotosHeatmapIndexHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val module = context.module
    private val transformer = SessionScopedHeatmapTransformer()
    private val observedCallCount = AtomicInteger()
    private lateinit var addItemsMethod: Method

    fun install(classLoader: ClassLoader) {
        val builderClass = classLoader.loadClass(S2IndexBuilderClassName)
        addItemsMethod = GooglePhotosHeatmapIndexMethodMatcher.find(builderClass)
            ?: error("S2 heatmap index add-items method is ambiguous or unavailable")
        addItemsMethod.isAccessible = true
        module.hook(addItemsMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                observeAndConvert(chain.thisObject, chain.args)
                chain.proceed()
            }
    }

    private fun observeAndConvert(receiver: Any?, arguments: List<Any?>) {
        val latitudes = arguments.getOrNull(LatitudeArgumentIndex) as? FloatArray
        val longitudes = arguments.getOrNull(LongitudeArgumentIndex) as? FloatArray
        val itemCount = arguments.getOrNull(ItemCountArgumentIndex) as? Int
        val session = sessionTracker.currentSession()
        val logSession = session?.toProbeLogSnapshot()
        val callOrdinal = observedCallCount.incrementAndGet()
        val callCount = logger.heatmapInvoked(
            invocationSnapshot(receiver, arguments, latitudes, longitudes, logSession, callOrdinal),
        )
        val result = transformer.transform(
            sessionId = session?.sessionId,
            receiver = receiver,
            latitudes = latitudes,
            longitudes = longitudes,
            itemCount = itemCount,
        )
        logger.heatmapArray(callCount, arraySnapshot(latitudes, longitudes, itemCount, result))
        logger.heatmapResult(result.outcome.logEvent, callCount, logSession, result)
    }

    private fun invocationSnapshot(
        receiver: Any?,
        arguments: List<Any?>,
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        session: ProbeSessionLogSnapshot?,
        callOrdinal: Int,
    ): HeatmapInvocationLogSnapshot {
        return HeatmapInvocationLogSnapshot(
            method = addItemsMethod.toGenericString(),
            receiverClass = receiver?.javaClass?.name,
            argumentTypes = arguments.joinToString(",") { it?.javaClass?.name ?: "null" },
            latitudeArraySize = latitudes?.size,
            longitudeArraySize = longitudes?.size,
            session = session,
            thread = Thread.currentThread().name,
            elapsedRealtime = SystemClock.elapsedRealtime(),
            filteredStack = if (callOrdinal <= StackTraceCallLimit) filteredStack() else null,
        )
    }

    private fun arraySnapshot(
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
        result: HeatmapSessionConversionResult,
    ): HeatmapArrayLogSnapshot {
        val sampleCount = validSampleCount(latitudes, longitudes, itemCount)
        return HeatmapArrayLogSnapshot(
            latSize = latitudes?.size,
            lngSize = longitudes?.size,
            sizeMatched = latitudes != null && longitudes != null && latitudes.size == longitudes.size,
            validCount = result.batchResult.validCount,
            chinaCount = result.batchResult.chinaCount,
            convertedCount = result.batchResult.convertedCount,
            firstSample = sampleAt(latitudes, longitudes, 0, sampleCount),
            middleSample = sampleAt(latitudes, longitudes, sampleCount / 2, sampleCount),
            lastSample = sampleAt(latitudes, longitudes, sampleCount - 1, sampleCount),
        )
    }

    private fun validSampleCount(
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
    ): Int {
        if (latitudes == null || longitudes == null || itemCount == null) return 0
        return itemCount.coerceIn(0, minOf(latitudes.size, longitudes.size))
    }

    private fun sampleAt(
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        index: Int,
        sampleCount: Int,
    ): String? {
        if (latitudes == null || longitudes == null || index !in 0 until sampleCount) return null
        return String.format(Locale.US, "%.6f,%.6f", latitudes[index], longitudes[index])
    }

    private fun filteredStack(): String {
        return Thread.currentThread().stackTrace
            .asSequence()
            .filter { frame -> StackClassPrefixes.any(frame.className::startsWith) }
            .take(MaximumStackFrames)
            .joinToString(" <- ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }
    }

    private companion object {
        private const val S2IndexBuilderClassName =
            "com.google.android.apps.photos.geo.S2Index\$BuilderImpl"
        private const val LatitudeArgumentIndex = 1
        private const val LongitudeArgumentIndex = 2
        private const val ItemCountArgumentIndex = 4
        private const val StackTraceCallLimit = 5
        private const val MaximumStackFrames = 12
        private val StackClassPrefixes = listOf(
            "com.google.android.apps.photos",
            "com.google.android.gms.maps",
            "com.google.maps",
        )
    }
}

internal object GooglePhotosHeatmapIndexMethodMatcher {
    private val expectedParameterTypes = arrayOf(
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
                method.parameterTypes.contentEquals(expectedParameterTypes)
        }
    }
}

internal enum class HeatmapConversionOutcome(val logEvent: String) {
    SKIPPED("skipped"),
    CONVERTED("converted"),
    FAILED("failed"),
}

internal data class HeatmapBatchConversionResult(
    val convertedCount: Int,
    val validCount: Int = 0,
    val chinaCount: Int = 0,
    val failure: Exception? = null,
    val failureReason: String? = null,
)

internal data class HeatmapSessionConversionResult(
    val outcome: HeatmapConversionOutcome,
    val reason: String,
    val batchResult: HeatmapBatchConversionResult = HeatmapBatchConversionResult(0),
) {
    val failure: Exception? = batchResult.failure
}

internal class SessionScopedHeatmapTransformer(
    private val converter: (Double, Double) -> Coordinate = ChinaCoordinateConverter::wgs84ToGcj02,
    private val conversionGuard: SessionObjectConversionGuard = SessionObjectConversionGuard(),
) {
    fun transform(
        sessionId: Long?,
        receiver: Any?,
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
    ): HeatmapSessionConversionResult {
        val inspection = HeatmapCoordinateBatchTransformer.inspect(latitudes, longitudes, itemCount)
        if (sessionId == null) return skipped("NO_ACTIVE_SESSION", inspection)
        if (receiver == null) return skipped("NO_RECEIVER", inspection)
        if (inspection.failure != null) return skipped(inspection.failureReason ?: "INVALID_BATCH", inspection)
        if (latitudes == null || longitudes == null || itemCount == null) {
            return skipped("INVALID_ARGUMENTS", inspection)
        }
        return convertActiveBatch(sessionId, receiver, latitudes, longitudes, itemCount)
    }

    private fun convertActiveBatch(
        sessionId: Long,
        receiver: Any,
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): HeatmapSessionConversionResult {
        val convertedLatitudes = latitudes.copyOf()
        val convertedLongitudes = longitudes.copyOf()
        val result = HeatmapCoordinateBatchTransformer.transform(
            convertedLatitudes,
            convertedLongitudes,
            itemCount,
            converter,
        )
        if (result.failure != null) return HeatmapSessionConversionResult(
            HeatmapConversionOutcome.FAILED,
            result.failureReason ?: "CONVERSION_FAILED",
            result,
        )
        if (result.convertedCount == 0) return skipped("NO_CHINA_COORDINATES", result)
        if (!conversionGuard.claim(receiver, sessionId)) {
            return skipped("ALREADY_CONVERTED", result.copy(convertedCount = 0))
        }
        convertedLatitudes.copyInto(latitudes)
        convertedLongitudes.copyInto(longitudes)
        return HeatmapSessionConversionResult(HeatmapConversionOutcome.CONVERTED, "WGS84_TO_GCJ02", result)
    }

    private fun skipped(
        reason: String,
        batchResult: HeatmapBatchConversionResult,
    ): HeatmapSessionConversionResult {
        return HeatmapSessionConversionResult(HeatmapConversionOutcome.SKIPPED, reason, batchResult)
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
        var chinaCount = 0
        repeat(itemCount) { index ->
            val latitude = latitudes[index].toDouble()
            val longitude = longitudes[index].toDouble()
            if (CoordinateValidator.isValid(latitude, longitude)) validCount += 1
            if (CoordinateValidator.isValid(latitude, longitude) &&
                CoordinateValidator.isInMainlandChina(latitude, longitude)
            ) {
                chinaCount += 1
            }
        }
        return HeatmapBatchConversionResult(0, validCount, chinaCount)
    }

    fun transform(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
        converter: (Double, Double) -> Coordinate = ChinaCoordinateConverter::wgs84ToGcj02,
    ): HeatmapBatchConversionResult {
        val inspection = inspect(latitudes, longitudes, itemCount)
        if (inspection.failure != null) return inspection
        val convertedLatitudes = latitudes.copyOf(itemCount)
        val convertedLongitudes = longitudes.copyOf(itemCount)
        val attempt = convertCoordinates(
            latitudes,
            longitudes,
            convertedLatitudes,
            convertedLongitudes,
            itemCount,
            converter,
        )
        if (attempt.failure != null) return conversionFailure(inspection, attempt.failure)
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
        converter: (Double, Double) -> Coordinate,
    ): HeatmapCoordinateConversionAttempt {
        var convertedCount = 0
        return try {
            repeat(itemCount) { index ->
                val original = Coordinate(latitudes[index].toDouble(), longitudes[index].toDouble())
                val converted = converter(original.latitude, original.longitude)
                if (converted != original) convertedCount += 1
                convertedLatitudes[index] = converted.latitude.toFloat()
                convertedLongitudes[index] = converted.longitude.toFloat()
            }
            HeatmapCoordinateConversionAttempt(convertedCount)
        } catch (error: Exception) {
            HeatmapCoordinateConversionAttempt(0, error)
        }
    }

    private fun conversionFailure(
        inspection: HeatmapBatchConversionResult,
        error: Exception,
    ): HeatmapBatchConversionResult {
        return inspection.copy(
            failure = error,
            failureReason = "CONVERSION_FAILED",
        )
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
