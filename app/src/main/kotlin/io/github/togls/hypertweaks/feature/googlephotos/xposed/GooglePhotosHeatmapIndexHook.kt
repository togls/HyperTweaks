package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal class GooglePhotosHeatmapIndexHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val scopeTracker: GooglePhotosMapScopeTracker,
) {
    private val module = context.module
    private val conversionLogged = AtomicBoolean(false)

    fun install(classLoader: ClassLoader) {
        val builderClass = classLoader.loadClass(S2IndexBuilderClassName)
        val addItemsMethod = GooglePhotosHeatmapIndexMethodMatcher.find(builderClass)
            ?: error("S2 heatmap index add-items method is ambiguous or unavailable")
        addItemsMethod.isAccessible = true
        module.hook(addItemsMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                applyCoordinateConversion(chain.args)
                chain.proceed()
            }
        logger.renderHookInstalled(HeatmapStrategy)
    }

    private fun applyCoordinateConversion(arguments: List<Any?>) {
        // The native S2 index can finish on a worker after onPause, so the bound Activity
        // session is the safe boundary; the index input is ephemeral and never writes media data.
        if (!scopeTracker.hasCollectionsMapSession()) {
            return
        }

        try {
            val latitudes = arguments.getOrNull(LatitudeArgumentIndex) as? FloatArray ?: return
            val longitudes = arguments.getOrNull(LongitudeArgumentIndex) as? FloatArray ?: return
            val itemCount = arguments.getOrNull(ItemCountArgumentIndex) as? Int ?: return
            val result = HeatmapCoordinateBatchTransformer.transform(
                latitudes = latitudes,
                longitudes = longitudes,
                itemCount = itemCount,
            )
            if (result.failure != null) {
                logger.warning("convert_heatmap_batch", result.failure)
                return
            }
            if (result.convertedCount > 0 && conversionLogged.compareAndSet(false, true)) {
                logger.conversionApplied(
                    target = HeatmapTarget,
                    convertedCount = result.convertedCount,
                )
            }
        } catch (error: Exception) {
            logger.warning("convert_heatmap_batch", error)
        }
    }

    private companion object {
        private const val S2IndexBuilderClassName =
            "com.google.android.apps.photos.geo.S2Index\$BuilderImpl"
        private const val HeatmapStrategy = "s2_index"
        private const val HeatmapTarget = "heatmap"
        private const val LatitudeArgumentIndex = 1
        private const val LongitudeArgumentIndex = 2
        private const val ItemCountArgumentIndex = 4
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
        // Photos renders its heatmap directly from this S2 batch and bypasses the Marker API.
        return builderClass.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                Modifier.isSynchronized(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(expectedParameterTypes)
        }
    }
}

internal data class HeatmapBatchConversionResult(
    val convertedCount: Int,
    val failure: Exception? = null,
)

internal object HeatmapCoordinateBatchTransformer {
    fun transform(
        latitudes: FloatArray,
        longitudes: FloatArray,
        itemCount: Int,
        converter: (Double, Double) -> Coordinate = ChinaCoordinateConverter::wgs84ToGcj02,
    ): HeatmapBatchConversionResult {
        if (itemCount !in 0..minOf(latitudes.size, longitudes.size)) {
            return HeatmapBatchConversionResult(
                convertedCount = 0,
                failure = IllegalArgumentException("Invalid heatmap coordinate batch size: $itemCount"),
            )
        }

        val convertedLatitudes = latitudes.copyOf(itemCount)
        val convertedLongitudes = longitudes.copyOf(itemCount)
        var convertedCount = 0
        try {
            repeat(itemCount) { index ->
                val originalLatitude = latitudes[index].toDouble()
                val originalLongitude = longitudes[index].toDouble()
                val converted = converter(originalLatitude, originalLongitude)
                if (converted.latitude != originalLatitude || converted.longitude != originalLongitude) {
                    convertedCount += 1
                }
                convertedLatitudes[index] = converted.latitude.toFloat()
                convertedLongitudes[index] = converted.longitude.toFloat()
            }
        } catch (error: Exception) {
            return HeatmapBatchConversionResult(convertedCount = 0, failure = error)
        }

        convertedLatitudes.copyInto(latitudes, endIndex = itemCount)
        convertedLongitudes.copyInto(longitudes, endIndex = itemCount)
        return HeatmapBatchConversionResult(convertedCount)
    }
}
