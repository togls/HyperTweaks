package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.os.SystemClock
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.CoordinateValidator
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal class GooglePhotosMapLocationHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val engine = context.engine
    private val readScope = MapLocationReadScope()
    private val requestTargets = CurrentLocationRequestRegistry()
    private val hookedRequestMethods = mutableSetOf<Method>()
    private val readReentry = ThreadLocal.withInitial { false }
    private val transformer = LocationCoordinateTransformer()
    private lateinit var renderBinding: MapRenderBinding
    private lateinit var latitudeMethod: Method
    private lateinit var longitudeMethod: Method

    fun install(classLoader: ClassLoader) {
        val coordinateClass = classLoader.loadClass(LatLngClassName)
        val activityClass = classLoader.loadClass(GooglePhotosClassNames.MapExploreActivity)
        renderBinding = GooglePhotosMapRenderMethodMatcher(coordinateClass)
            .inspect(activityClass)
            .binding ?: error("Marker render method is ambiguous or unavailable")
        val locationClass = classLoader.loadClass(LocationClassName)
        latitudeMethod = locationClass.getDeclaredMethod("getLatitude").apply { isAccessible = true }
        longitudeMethod = locationClass.getDeclaredMethod("getLongitude").apply { isAccessible = true }
        installCoordinateGetter(latitudeMethod, CoordinateAxis.LATITUDE)
        installCoordinateGetter(longitudeMethod, CoordinateAxis.LONGITUDE)
    }

    fun onActivityAvailable(activity: Activity) {
        if (!::renderBinding.isInitialized || activity.javaClass != renderBinding.controllerField.declaringClass) {
            return
        }
        val controller = renderBinding.controllerField.get(activity) ?: return
        val report = CurrentLocationRequestResolver().inspect(controller, renderBinding.facadeField)
        logger.locationMatcherCompleted(report)
        val binding = report.binding ?: error("Current location request method is ambiguous or unavailable")
        requestTargets.register(binding.receiver)
        installRequestInterceptor(binding.method)
    }

    private fun installCoordinateGetter(method: Method, axis: CoordinateAxis) {
        engine.hook(method) { chain -> interceptCoordinateRead(chain, axis) }
    }

    private fun interceptCoordinateRead(chain: HookChain, axis: CoordinateAxis): Any? {
        val originalResult = chain.proceed()
        val originalAxis = originalResult as? Double ?: return originalResult
        val location = chain.thisObject ?: return originalAxis
        if (readReentry.get() == true) return originalAxis
        val session = sessionTracker.currentSession() ?: return originalAxis
        val decision = readScope.decide(location, session.sessionId, callerClasses()) ?: return originalAxis
        val original = readCoordinate(location, axis, originalAxis) ?: return originalAxis
        val result = transformer.transform(original)
        logger.locationRead(axis, decision, session.toProbeLogSnapshot(), result)
        return result.converted?.value(axis) ?: originalAxis
    }

    private fun readCoordinate(location: Any, axis: CoordinateAxis, originalAxis: Double): Coordinate? {
        readReentry.set(true)
        return try {
            when (axis) {
                CoordinateAxis.LATITUDE -> Coordinate(originalAxis, longitudeMethod.invoke(location) as Double)
                CoordinateAxis.LONGITUDE -> Coordinate(latitudeMethod.invoke(location) as Double, originalAxis)
            }
        } catch (error: Exception) {
            logger.warning("location_read_coordinate", error)
            null
        } finally {
            readReentry.set(false)
        }
    }

    @Synchronized
    private fun installRequestInterceptor(method: Method) {
        if (!hookedRequestMethods.add(method)) return
        engine.hook(method) { chain ->
                armRequest(chain.thisObject)
                chain.proceed()
            }
    }

    private fun armRequest(receiver: Any?) {
        if (receiver == null || !requestTargets.contains(receiver)) return
        val session = sessionTracker.currentSession() ?: return
        readScope.arm(session.sessionId)
        logger.locationRequestArmed(receiver.javaClass.name, session.toProbeLogSnapshot())
    }

    private fun callerClasses(): List<String> {
        return Thread.currentThread().stackTrace.map(StackTraceElement::getClassName)
    }

    private companion object {
        private const val LatLngClassName = "com.google.android.gms.maps.model.LatLng"
        private const val LocationClassName = "android.location.Location"
    }
}

internal enum class CoordinateAxis {
    LATITUDE,
    LONGITUDE,
}

private fun Coordinate.value(axis: CoordinateAxis): Double {
    return when (axis) {
        CoordinateAxis.LATITUDE -> latitude
        CoordinateAxis.LONGITUDE -> longitude
    }
}

internal enum class LocationCoordinateOutcome {
    CONVERTED,
    UNCHANGED,
    FAILED,
}

internal data class LocationCoordinateResult(
    val outcome: LocationCoordinateOutcome,
    val reason: String,
    val original: Coordinate,
    val converted: Coordinate? = null,
    val failure: Exception? = null,
)

internal class LocationCoordinateTransformer(
    private val converter: (Double, Double) -> Coordinate = ChinaCoordinateConverter::wgs84ToGcj02,
) {
    fun transform(original: Coordinate): LocationCoordinateResult {
        if (!CoordinateValidator.isValid(original.latitude, original.longitude)) {
            return unchanged("INVALID_COORDINATE", original)
        }
        if (!CoordinateValidator.isInMainlandChina(original.latitude, original.longitude)) {
            return unchanged("OUTSIDE_CHINA", original)
        }
        return try {
            val converted = converter(original.latitude, original.longitude)
            if (converted == original) unchanged("NO_OFFSET", original) else LocationCoordinateResult(
                outcome = LocationCoordinateOutcome.CONVERTED,
                reason = "WGS84_TO_GCJ02",
                original = original,
                converted = converted,
            )
        } catch (error: Exception) {
            LocationCoordinateResult(LocationCoordinateOutcome.FAILED, "CONVERSION_FAILED", original, failure = error)
        }
    }

    private fun unchanged(reason: String, original: Coordinate): LocationCoordinateResult {
        return LocationCoordinateResult(LocationCoordinateOutcome.UNCHANGED, reason, original, original)
    }
}

internal enum class MapLocationReadSource {
    CURRENT_LOCATION_REQUEST,
    MAPS_LOCATION_LAYER,
}

internal data class MapLocationReadDecision(
    val source: MapLocationReadSource,
    val callerClass: String,
)

internal class MapLocationReadScope(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val requestTimeoutMillis: Long = DefaultRequestTimeoutMillis,
) {
    private val knownLocations = WeakHashMap<Any, Long>()
    private var armedSessionId: Long? = null
    private var requestExpiresAt = 0L

    @Synchronized
    fun arm(sessionId: Long) {
        armedSessionId = sessionId
        requestExpiresAt = clock() + requestTimeoutMillis
    }

    @Synchronized
    fun decide(location: Any, sessionId: Long, callers: List<String>): MapLocationReadDecision? {
        mapLayerCaller(callers)?.let { caller ->
            return MapLocationReadDecision(MapLocationReadSource.MAPS_LOCATION_LAYER, caller)
        }
        if (knownLocations[location] == sessionId) {
            return MapLocationReadDecision(MapLocationReadSource.CURRENT_LOCATION_REQUEST, firstAppCaller(callers))
        }
        if (armedSessionId != sessionId || clock() > requestExpiresAt) {
            clearExpiredRequest()
            return null
        }
        knownLocations[location] = sessionId
        armedSessionId = null
        return MapLocationReadDecision(MapLocationReadSource.CURRENT_LOCATION_REQUEST, firstAppCaller(callers))
    }

    private fun mapLayerCaller(callers: List<String>): String? {
        return callers.firstOrNull { caller -> MapsPrefixes.any(caller::startsWith) }
    }

    private fun firstAppCaller(callers: List<String>): String {
        return callers.firstOrNull { caller -> IgnoredCallerPrefixes.none(caller::startsWith) } ?: "unknown"
    }

    private fun clearExpiredRequest() {
        if (clock() > requestExpiresAt) armedSessionId = null
    }

    private companion object {
        // 定位结果可能来自异步任务；窗口过短会漏掉首次定位，过长则会扩大坐标读取范围。
        private const val DefaultRequestTimeoutMillis = 30_000L
        private val MapsPrefixes = listOf("com.google.maps.", "com.google.android.gms.maps.")
        private val IgnoredCallerPrefixes = listOf(
            "java.",
            "android.location.",
            "io.github.togls.hypertweaks.",
        )
    }
}

internal data class CurrentLocationRequestBinding(
    val receiver: Any,
    val method: Method,
)

internal data class CurrentLocationRequestMatchReport(
    val controllerCandidateCount: Int,
    val methodCandidateCount: Int,
    val binding: CurrentLocationRequestBinding?,
)

internal class CurrentLocationRequestResolver {
    fun inspect(controller: Any, facadeField: Field): CurrentLocationRequestMatchReport {
        val candidates = controller.javaClass.declaredFields.mapNotNull { field ->
            requestCandidate(field, controller, facadeField.type)
        }
        val methodCount = candidates.sumOf { it.methods.size }
        val candidate = candidates.singleOrNull()
        val binding = candidate?.methods?.singleOrNull()?.let { method ->
            CurrentLocationRequestBinding(candidate.receiver, method.apply { isAccessible = true })
        }
        return CurrentLocationRequestMatchReport(
            controllerCandidateCount = candidates.size,
            methodCandidateCount = methodCount,
            binding = binding,
        )
    }

    private fun requestCandidate(
        field: Field,
        controller: Any,
        facadeClass: Class<*>,
    ): CurrentLocationRequestCandidate? {
        if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) return null
        field.isAccessible = true
        val receiver = field.get(controller) ?: return null
        val matchingFacadeFields = receiver.javaClass.declaredFields.filter { nested ->
            !Modifier.isStatic(nested.modifiers) && nested.type == facadeClass
        }
        if (matchingFacadeFields.size != 1) return null
        val methods = receiver.javaClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.parameterCount == 0 && method.returnType == Void.TYPE
        }
        return CurrentLocationRequestCandidate(receiver, methods)
    }
}

internal data class CurrentLocationRequestCandidate(
    val receiver: Any,
    val methods: List<Method>,
)

internal class CurrentLocationRequestRegistry {
    private val receivers = WeakHashMap<Any, Unit>()

    @Synchronized
    fun register(receiver: Any) {
        receivers[receiver] = Unit
    }

    @Synchronized
    fun contains(receiver: Any): Boolean = receivers.containsKey(receiver)
}
