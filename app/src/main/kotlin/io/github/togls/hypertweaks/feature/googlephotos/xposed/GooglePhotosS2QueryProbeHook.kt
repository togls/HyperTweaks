package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

internal class GooglePhotosS2QueryHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val module = context.module
    private val coordinatePolicy = S2QueryCoordinatePolicy()
    private val observedQueryCount = AtomicInteger()

    fun install(classLoader: ClassLoader) {
        val indexClass = classLoader.loadClass(S2IndexClassName)
        val resultClass = classLoader.loadClass(S2ResultClassName)
        installQueryHook(resolveQueryMethod(indexClass))
        installResultCountHook(resolveResultCountMethod(indexClass, resultClass))
    }

    private fun installQueryHook(method: Method) {
        method.isAccessible = true
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val bounds = S2QueryBoundsReader.read(chain.args)
                val session = sessionTracker.currentSession()?.toProbeLogSnapshot()
                // S2 索引保留照片原始 WGS 数据，只在地图视图进入数据查询时执行逆转换。
                val dataBounds = bounds?.let {
                    coordinatePolicy.transform(it, sessionActive = session != null)
                }
                val diagnostics = diagnosticContext(observedQueryCount.incrementAndGet())
                val callCount = logger.s2QueryInvoked(
                    bounds = bounds,
                    dataBounds = dataBounds,
                    session = session,
                    thread = Thread.currentThread().name,
                    caller = diagnostics.caller,
                    stack = diagnostics.stack,
                )
                val result = dataBounds?.let { data ->
                    chain.proceed(S2QueryBoundsWriter.write(chain.args, data))
                } ?: chain.proceed()
                logger.s2QueryCompleted(callCount, result as? Long)
                result
            }
    }

    private fun installResultCountHook(method: Method) {
        method.isAccessible = true
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                logger.s2QueryResultCount(
                    resultHandle = chain.args.getOrNull(ResultHandleArgumentIndex) as? Long,
                    itemCount = result as? Int,
                )
                result
            }
    }

    private fun resolveQueryMethod(indexClass: Class<*>): Method {
        return indexClass.getDeclaredMethod(
            QueryMethodName,
            indexClass,
            Long::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            LongArray::class.java,
            LongArray::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
    }

    private fun resolveResultCountMethod(indexClass: Class<*>, resultClass: Class<*>): Method {
        return indexClass.getDeclaredMethod(
            ResultCountMethodName,
            resultClass,
            Long::class.javaPrimitiveType,
        )
    }

    private fun diagnosticContext(callCount: Int): S2QueryDiagnosticContext {
        if (!shouldIncludeDiagnostics(callCount)) {
            return S2QueryDiagnosticContext(RateLimitedValue, RateLimitedValue)
        }
        val frames = probeFrames()
        return S2QueryDiagnosticContext(
            caller = frames.firstOrNull()?.className ?: "unknown",
            stack = frames.joinToString(" <- ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            },
        )
    }

    private fun probeFrames(): List<StackTraceElement> {
        return Thread.currentThread().stackTrace
            .asSequence()
            .filterNot { frame -> IgnoredStackPrefixes.any(frame.className::startsWith) }
            .take(MaximumStackFrames)
            .toList()
    }

    private fun shouldIncludeDiagnostics(callCount: Int): Boolean {
        return callCount <= DetailedQueryLimit || callCount % QuerySummaryInterval == 0
    }

    private companion object {
        private const val S2IndexClassName = "com.google.android.apps.photos.geo.S2Index"
        private const val S2ResultClassName = "com.google.android.apps.photos.geo.S2Index\$ResultImpl"
        private const val QueryMethodName = "nativeIndexQuery"
        private const val ResultCountMethodName = "nativeResultGetCount"
        private const val ResultHandleArgumentIndex = 1
        private const val MaximumStackFrames = 12
        private const val DetailedQueryLimit = 50
        private const val QuerySummaryInterval = 100
        private const val RateLimitedValue = "rate_limited"
        private val IgnoredStackPrefixes = listOf(
            "java.lang.Thread",
            "io.github.togls.hypertweaks.",
            "io.github.libxposed.",
            "org.lsposed.",
        )
    }
}

private data class S2QueryDiagnosticContext(
    val caller: String,
    val stack: String,
)

internal data class S2QueryBounds(
    val minimumLatitude: Double,
    val minimumLongitude: Double,
    val maximumLatitude: Double,
    val maximumLongitude: Double,
)

internal object S2QueryBoundsReader {
    private const val MinimumLatitudeArgumentIndex = 2
    private const val MinimumLongitudeArgumentIndex = 3
    private const val MaximumLatitudeArgumentIndex = 4
    private const val MaximumLongitudeArgumentIndex = 5

    fun read(arguments: List<Any?>): S2QueryBounds? {
        val minimumLatitude = arguments.getOrNull(MinimumLatitudeArgumentIndex) as? Float ?: return null
        val minimumLongitude = arguments.getOrNull(MinimumLongitudeArgumentIndex) as? Float ?: return null
        val maximumLatitude = arguments.getOrNull(MaximumLatitudeArgumentIndex) as? Float ?: return null
        val maximumLongitude = arguments.getOrNull(MaximumLongitudeArgumentIndex) as? Float ?: return null
        return S2QueryBounds(
            minimumLatitude = minimumLatitude.toDouble(),
            minimumLongitude = minimumLongitude.toDouble(),
            maximumLatitude = maximumLatitude.toDouble(),
            maximumLongitude = maximumLongitude.toDouble(),
        )
    }
}

internal object S2QueryBoundsWriter {
    fun write(arguments: List<Any?>, bounds: S2QueryBounds): Array<Any?> {
        return arguments.toTypedArray().apply {
            this[MinimumLatitudeArgumentIndex] = bounds.minimumLatitude.toFloat()
            this[MinimumLongitudeArgumentIndex] = bounds.minimumLongitude.toFloat()
            this[MaximumLatitudeArgumentIndex] = bounds.maximumLatitude.toFloat()
            this[MaximumLongitudeArgumentIndex] = bounds.maximumLongitude.toFloat()
        }
    }

    private const val MinimumLatitudeArgumentIndex = 2
    private const val MinimumLongitudeArgumentIndex = 3
    private const val MaximumLatitudeArgumentIndex = 4
    private const val MaximumLongitudeArgumentIndex = 5
}

internal class S2QueryBoundsTransformer(
    private val converter: (Double, Double) -> Coordinate = ChinaCoordinateConverter::gcj02ToWgs84,
) {
    fun transform(bounds: S2QueryBounds): S2QueryBounds? {
        val minimum = transformCoordinate(bounds.minimumLatitude, bounds.minimumLongitude) ?: return null
        val maximum = transformCoordinate(bounds.maximumLatitude, bounds.maximumLongitude) ?: return null
        return S2QueryBounds(
            minimumLatitude = minOf(minimum.latitude, maximum.latitude),
            minimumLongitude = minOf(minimum.longitude, maximum.longitude),
            maximumLatitude = maxOf(minimum.latitude, maximum.latitude),
            maximumLongitude = maxOf(minimum.longitude, maximum.longitude),
        )
    }

    private fun transformCoordinate(latitude: Double, longitude: Double): Coordinate? {
        if (!CoordinateValidator.isValid(latitude, longitude)) return null
        return converter(latitude, longitude)
    }
}

internal class S2QueryCoordinatePolicy(
    private val transformer: S2QueryBoundsTransformer = S2QueryBoundsTransformer(),
) {
    fun transform(bounds: S2QueryBounds, sessionActive: Boolean): S2QueryBounds? {
        if (!sessionActive) return null
        return transformer.transform(bounds)
    }
}
