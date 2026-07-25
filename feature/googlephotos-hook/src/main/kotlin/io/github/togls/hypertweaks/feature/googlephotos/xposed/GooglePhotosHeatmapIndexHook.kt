package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTarget
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTargetResolver
import io.github.togls.hypertweaks.feature.googlephotos.session.GooglePhotosMapSessionTracker
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class GooglePhotosHeatmapIndexHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val engine = context.engine
    private val coordinateObserver = HeatmapCoordinateObserver()
    private lateinit var addItemsMethod: Method

    fun install(resolver: GooglePhotosTargetResolver) {
        val builderClass = resolver.resolve(GooglePhotosTarget.S2_BUILDER).targetClass
        addItemsMethod = GooglePhotosHeatmapIndexMethodMatcher.find(builderClass)
            ?: error("S2 heatmap index add-items method is ambiguous or unavailable")
        resolver.bindingSelected(GooglePhotosTarget.S2_BUILDER, addItemsMethod.toGenericString())
        addItemsMethod.isAccessible = true
        engine.hook(addItemsMethod) { chain ->
            observeBatch(chain.thisObject, chain.args)
            chain.proceed()
        }
    }

    private fun observeBatch(receiver: Any?, arguments: List<Any?>) {
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
        /*
         * S2 索引必须保留照片的 WGS84 坐标；地图瓦片查询会在 S2Query Hook 中
         * 将 GCJ02 视图边界逆转换为 WGS84。修改索引会让热力图渲染与点击查询错位。
         */
        val result = coordinateObserver.observe(latitudes, longitudes, itemCount)
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

internal class HeatmapCoordinateObserver {
    fun observe(
        latitudes: FloatArray?,
        longitudes: FloatArray?,
        itemCount: Int?,
    ): HeatmapConversionResult {
        val inspection = HeatmapCoordinateBatchTransformer.inspect(latitudes, longitudes, itemCount)
        if (inspection.failure != null) {
            return HeatmapConversionResult(
                HeatmapConversionOutcome.SKIPPED,
                inspection.failureReason ?: "INVALID_BATCH",
                inspection,
            )
        }
        return HeatmapConversionResult(
            HeatmapConversionOutcome.SKIPPED,
            "S2_INDEX_WGS84_PRESERVED",
            inspection,
        )
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

    private fun invalidBatch(reason: String): HeatmapBatchConversionResult {
        return HeatmapBatchConversionResult(
            convertedCount = 0,
            failure = IllegalArgumentException(reason),
            failureReason = reason,
        )
    }
}
