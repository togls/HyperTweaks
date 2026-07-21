package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.os.SystemClock
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.WeakHashMap
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
    private val converter: (Double, Double) -> Coordinate =
        ChinaCoordinateConverter::wgs84ToGcj02,

    /*
     * 防止同一数组、同一批已转换内容被再次转换。
     *
     * 不再使用 S2Index.BuilderImpl 作为防重键，
     * 因为一个 Builder 会连续提交多批数据。
     */
    private val conversionGuard: HeatmapBatchConversionGuard =
        HeatmapBatchConversionGuard(),
) {
    fun transform(
        sessionId: Long?,
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
    ): HeatmapSessionConversionResult {
        val inspection = HeatmapCoordinateBatchTransformer.inspect(
            latitudes,
            longitudes,
            itemCount,
        )

        if (sessionId == null) {
            return skipped(
                "NO_ACTIVE_SESSION",
                inspection,
            )
        }

        if (inspection.failure != null) {
            return skipped(
                inspection.failureReason ?: "INVALID_BATCH",
                inspection,
            )
        }

        if (
            latitudes == null ||
            longitudes == null ||
            itemCount == null
        ) {
            return skipped(
                "INVALID_ARGUMENTS",
                inspection,
            )
        }

        /*
         * 当前数组中的数据恰好等于上次模块写入的转换结果时，
         * 才认定为重复调用。
         *
         * 如果 Google Photos 复用数组并写入了新的 WGS84 坐标，
         * 指纹会变化，仍然会正常转换。
         */
        if (
            conversionGuard.isAlreadyConverted(
                latitudes,
                longitudes,
                itemCount,
            )
        ) {
            return skipped(
                "ALREADY_CONVERTED",
                inspection,
            )
        }

        return convertActiveBatch(
            latitudes,
            longitudes,
            itemCount,
        )
    }

    private fun convertActiveBatch(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): HeatmapSessionConversionResult {
        /*
         * 先在副本中转换，保证转换过程中任意一个坐标失败时，
         * 原始批次不会被部分修改。
         */
        val convertedLatitudes = latitudes.copyOf()
        val convertedLongitudes = longitudes.copyOf()

        val result = HeatmapCoordinateBatchTransformer.transform(
            convertedLatitudes,
            convertedLongitudes,
            itemCount,
            converter,
        )

        if (result.failure != null) {
            return HeatmapSessionConversionResult(
                outcome = HeatmapConversionOutcome.FAILED,
                reason = result.failureReason ?: "CONVERSION_FAILED",
                batchResult = result,
            )
        }

        if (result.convertedCount == 0) {
            return skipped(
                "NO_CHINA_COORDINATES",
                result,
            )
        }

        /*
         * 只写回 itemCount 范围。
         * 数组尾部可能是 Builder 预分配但尚未使用的空间。
         */
        convertedLatitudes.copyInto(
            destination = latitudes,
            endIndex = itemCount,
        )

        convertedLongitudes.copyInto(
            destination = longitudes,
            endIndex = itemCount,
        )

        conversionGuard.record(
            latitudes,
            longitudes,
            itemCount,
        )

        return HeatmapSessionConversionResult(
            outcome = HeatmapConversionOutcome.CONVERTED,
            reason = "WGS84_TO_GCJ02",
            batchResult = result,
        )
    }

    private fun skipped(
        reason: String,
        batchResult: HeatmapBatchConversionResult,
    ): HeatmapSessionConversionResult {
        return HeatmapSessionConversionResult(
            outcome = HeatmapConversionOutcome.SKIPPED,
            reason = reason,
            batchResult = batchResult,
        )
    }
}

internal data class HeatmapBatchConversionStamp(
    /*
     * 纬度数组作为 WeakHashMap 的弱键。
     * 经度数组使用 WeakReference，避免 Guard 延长数组生命周期。
     */
    val longitudeArray: WeakReference<FloatArray>,
    val itemCount: Int,
    val convertedFingerprint: Long,
)

internal class HeatmapBatchConversionGuard {
    private val stamps =
        WeakHashMap<FloatArray, HeatmapBatchConversionStamp>()

    @Synchronized
    fun isAlreadyConverted(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): Boolean {
        val stamp = stamps[latitudes] ?: return false

        if (stamp.longitudeArray.get() !== longitudes) {
            return false
        }

        if (stamp.itemCount != itemCount) {
            return false
        }

        return stamp.convertedFingerprint ==
            fingerprint(
                latitudes,
                longitudes,
                itemCount,
            )
    }

    @Synchronized
    fun record(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ) {
        stamps[latitudes] = HeatmapBatchConversionStamp(
            longitudeArray = WeakReference(longitudes),
            itemCount = itemCount,
            convertedFingerprint = fingerprint(
                latitudes,
                longitudes,
                itemCount,
            ),
        )
    }

    private fun fingerprint(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
    ): Long {
        var hash = FingerprintSeed

        repeat(itemCount) { index ->
            hash =
                FingerprintMultiplier * hash +
                    latitudes[index].toRawBits()

            hash =
                FingerprintMultiplier * hash +
                    longitudes[index].toRawBits()
        }

        return hash
    }

    private companion object {
        private const val FingerprintSeed =
            1125899906842597L

        private const val FingerprintMultiplier =
            31L
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
