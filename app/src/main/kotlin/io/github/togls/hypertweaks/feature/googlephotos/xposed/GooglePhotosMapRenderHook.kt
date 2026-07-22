package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal class GooglePhotosMapRenderHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val module = context.module
    private val transformer = MarkerCoordinateTransformer()
    private lateinit var coordinateAccessors: CoordinateAccessors
    private lateinit var renderBinding: MapRenderBinding

    fun install(classLoader: ClassLoader) {
        val coordinateClass = classLoader.loadClass(LatLngClassName)
        coordinateAccessors = CoordinateAccessorResolver.resolve(coordinateClass)
            ?: error("LatLng accessors are ambiguous")
        val activityClass = classLoader.loadClass(GooglePhotosClassNames.MapExploreActivity)
        logger.markerMatcherStart(activityClass.name)
        val report = GooglePhotosMapRenderMethodMatcher(coordinateClass).inspect(activityClass)
        logger.markerMatcherCompleted(report)
        renderBinding = report.binding
            ?: error("Marker render method is ambiguous or unavailable")
        installRenderInterceptor()
    }

    private fun installRenderInterceptor() {
        renderBinding.method.isAccessible = true
        module.hook(renderBinding.method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                observeAndConvert(chain.thisObject, chain.args.firstOrNull())
                chain.proceed()
            }
    }

    private fun observeAndConvert(receiver: Any?, markerOptions: Any?) {
        val markerPosition = markerOptions?.let(::readMarkerPosition)

        // Session 只保留为诊断上下文；转换范围由 Google Photos 包与主进程边界决定。
        val logSession = sessionTracker.currentSession()?.toProbeLogSnapshot()
        val callCount = logger.markerInvoked(
            method = renderBinding.method.toGenericString(),
            receiverClass = receiver?.javaClass?.name,
            session = logSession,
            coordinate = markerPosition?.position?.coordinate,
        )
        if (markerOptions == null) {
            logger.markerResult("skipped", callCount, logSession, MarkerConversionResult.noTarget())
            return
        }
        convertMarker(
            markerOptions,
            markerPosition ?: MarkerPositionReadResult(),
            callCount,
            logSession,
        )
    }

    private fun convertMarker(
        markerOptions: Any,
        markerPosition: MarkerPositionReadResult,
        callCount: Int,
        logSession: ProbeSessionLogSnapshot?,
    ) {
        if (markerPosition.failure != null) {
            logger.markerResult("failed", callCount, logSession, markerPosition.failure)
            return
        }
        val originalPosition = markerPosition.position ?: run {
            logger.markerResult("skipped", callCount, logSession, MarkerConversionResult.noPosition())
            return
        }
        val result = transformer.transform(markerOptions, originalPosition.coordinate) {
            renderBinding.positionField.set(markerOptions, coordinateAccessors.create(it))
        }
        logger.markerResult(result.outcome.logEvent, callCount, logSession, result)
    }

    private fun readMarkerPosition(markerOptions: Any): MarkerPositionReadResult {
        return try {
            val value = renderBinding.positionField.get(markerOptions)
                ?: return MarkerPositionReadResult()
            MarkerPositionReadResult(MarkerPosition(value, coordinateAccessors.read(value)))
        } catch (error: Exception) {
            MarkerPositionReadResult(
                failure = MarkerConversionResult.failed("READ_POSITION_FAILED", error),
            )
        }
    }

    private companion object {
        private const val LatLngClassName = "com.google.android.gms.maps.model.LatLng"
    }
}

internal enum class MarkerConversionOutcome(val logEvent: String) {
    SKIPPED("skipped"),
    CONVERTED("converted"),
    UNCHANGED("unchanged"),
    FAILED("failed"),
}

internal data class MarkerConversionResult(
    val outcome: MarkerConversionOutcome,
    val reason: String,
    val original: Coordinate? = null,
    val converted: Coordinate? = null,
    val failure: Exception? = null,
) {
    companion object {
        fun noTarget() = MarkerConversionResult(MarkerConversionOutcome.SKIPPED, "NO_MARKER_OPTIONS")
        fun noPosition() = MarkerConversionResult(MarkerConversionOutcome.SKIPPED, "NO_POSITION")
        fun failed(reason: String, error: Exception) = MarkerConversionResult(
            outcome = MarkerConversionOutcome.FAILED,
            reason = reason,
            failure = error,
        )
    }
}

internal class MarkerCoordinateTransformer(
    private val converter: (Double, Double) -> Coordinate =
        ChinaCoordinateConverter::wgs84ToGcj02,

    private val conversionGuard: MarkerCoordinateConversionGuard =
        MarkerCoordinateConversionGuard(),
) {
    fun transform(
        target: Any,
        original: Coordinate,
        applyConversion: (Coordinate) -> Unit,
    ): MarkerConversionResult {
        if (
            !CoordinateValidator.isValid(
                original.latitude,
                original.longitude,
            )
        ) {
            return unchanged(
                "INVALID_COORDINATE",
                original,
            )
        }

        /*
         * 必须在 China 范围判断和 converter 调用之前检查。
         *
         * 已转换后的 GCJ02 坐标仍然位于中国范围内，
         * 如果先执行 converter，就会产生二次偏移。
         */
        if (
            conversionGuard.isAlreadyConverted(
                target,
                original,
            )
        ) {
            return skipped(
                "ALREADY_CONVERTED",
                original,
            )
        }

        if (
            !CoordinateValidator.isInMainlandChina(
                original.latitude,
                original.longitude,
            )
        ) {
            return unchanged(
                "OUTSIDE_CHINA",
                original,
            )
        }

        val converted = try {
            converter(
                original.latitude,
                original.longitude,
            )
        } catch (error: Exception) {
            return MarkerConversionResult
                .failed(
                    "CONVERSION_FAILED",
                    error,
                )
                .copy(original = original)
        }

        if (converted == original) {
            return unchanged(
                "NO_OFFSET",
                original,
            )
        }

        return applyConversion(
            target = target,
            original = original,
            converted = converted,
            update = applyConversion,
        )
    }

    private fun applyConversion(
        target: Any,
        original: Coordinate,
        converted: Coordinate,
        update: (Coordinate) -> Unit,
    ): MarkerConversionResult {
        return try {
            update(converted)

            /*
             * 只有真正写入成功后才记录。
             */
            conversionGuard.record(
                target,
                converted,
            )

            MarkerConversionResult(
                outcome = MarkerConversionOutcome.CONVERTED,
                reason = "WGS84_TO_GCJ02",
                original = original,
                converted = converted,
            )
        } catch (error: Exception) {
            MarkerConversionResult
                .failed(
                    "WRITE_POSITION_FAILED",
                    error,
                )
                .copy(original = original)
        }
    }

    private fun skipped(
        reason: String,
        original: Coordinate,
    ): MarkerConversionResult {
        return MarkerConversionResult(
            outcome = MarkerConversionOutcome.SKIPPED,
            reason = reason,
            original = original,
        )
    }

    private fun unchanged(
        reason: String,
        original: Coordinate,
    ): MarkerConversionResult {
        return MarkerConversionResult(
            outcome = MarkerConversionOutcome.UNCHANGED,
            reason = reason,
            original = original,
            converted = original,
        )
    }
}

/**
 * 记录模块最后一次真正写入 MarkerOptions 的坐标。
 *
 * 只有当前坐标仍然等于模块上次写入值时，才判定为重复转换。
 * 如果 Google Photos 复用 MarkerOptions 并写入新 WGS84 坐标，
 * 新坐标与 stamp 不同，会再次转换。
 */
internal class MarkerCoordinateConversionGuard {
    private val convertedCoordinates =
        WeakHashMap<Any, Coordinate>()

    @Synchronized
    fun isAlreadyConverted(
        target: Any,
        current: Coordinate,
    ): Boolean {
        return convertedCoordinates[target] == current
    }

    @Synchronized
    fun record(
        target: Any,
        converted: Coordinate,
    ) {
        convertedCoordinates[target] = converted
    }
}

internal data class MarkerPosition(
    val value: Any,
    val coordinate: Coordinate,
)

internal data class MarkerPositionReadResult(
    val position: MarkerPosition? = null,
    val failure: MarkerConversionResult? = null,
)

internal data class MapRenderBinding(
    val method: Method,
    val positionField: Field,
)

internal data class MapRenderMatchReport(
    val controllerCandidateCount: Int,
    val facadeCandidateCount: Int,
    val bindings: List<MapRenderBinding>,
) {
    val binding: MapRenderBinding? = bindings.singleOrNull()
}

internal class GooglePhotosMapRenderMethodMatcher(
    private val coordinateClass: Class<*>,
) {
    fun inspect(activityClass: Class<*>): MapRenderMatchReport {
        val controllerTypes = activityClass.declaredFields
            .asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map(Field::getType)
            .filter { it.classLoader === activityClass.classLoader }
            .distinct()
            .toList()
        val facadeTypes = controllerTypes
            .asSequence()
            .flatMap { controllerType -> controllerType.declaredFields.asSequence() }
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map(Field::getType)
            .filter { it.classLoader === activityClass.classLoader }
            .distinct()
            .toList()
        val bindings = facadeTypes.flatMap(::candidateBindings)
            .distinctBy(MapRenderBinding::method)
        return MapRenderMatchReport(controllerTypes.size, facadeTypes.size, bindings)
    }

    private fun candidateBindings(facadeType: Class<*>): List<MapRenderBinding> {
        return facadeType.declaredMethods
            .asSequence()
            .filter { method -> method.parameterCount == 1 && method.returnType != Void.TYPE }
            .mapNotNull(::bindingFor)
            .toList()
    }

    private fun bindingFor(method: Method): MapRenderBinding? {
        val optionsType = method.parameterTypes.single()
        val positionFields = optionsType.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == coordinateClass
        }
        if (positionFields.size != 1 || !returnTypeExposesCoordinate(method.returnType)) return null
        return MapRenderBinding(
            method = method,
            positionField = positionFields.single().apply { isAccessible = true },
        )
    }

    private fun returnTypeExposesCoordinate(returnType: Class<*>): Boolean {
        return returnType.declaredMethods.any { method ->
            method.parameterCount == 0 && method.returnType == coordinateClass
        }
    }
}

internal data class CoordinateAccessors(
    val constructor: Constructor<*>,
    val latitudeField: Field,
    val longitudeField: Field,
) {
    fun read(value: Any): Coordinate {
        return Coordinate(
            latitude = latitudeField.getDouble(value),
            longitude = longitudeField.getDouble(value),
        )
    }

    fun create(coordinate: Coordinate): Any {
        return constructor.newInstance(coordinate.latitude, coordinate.longitude)
    }
}

internal object CoordinateAccessorResolver {
    fun resolve(coordinateClass: Class<*>): CoordinateAccessors? {
        val constructor = coordinateClass.getDeclaredConstructor(
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val coordinateFields = coordinateClass.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == Double::class.javaPrimitiveType
        }.onEach { field -> field.isAccessible = true }
        if (coordinateFields.size != 2) return null

        val probe = constructor.newInstance(LatitudeProbe, LongitudeProbe)
        val latitudeField = coordinateFields.singleOrNull { it.getDouble(probe) == LatitudeProbe }
        val longitudeField = coordinateFields.singleOrNull { it.getDouble(probe) == LongitudeProbe }
        if (latitudeField == null || longitudeField == null || latitudeField == longitudeField) return null
        return CoordinateAccessors(constructor, latitudeField, longitudeField)
    }

    private const val LatitudeProbe = 12.3456789
    private const val LongitudeProbe = 98.7654321
}
