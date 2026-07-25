package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.os.SystemClock
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.logging.GooglePhotosDiagnosticsPolicy
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTarget
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTargetResolver
import io.github.togls.hypertweaks.feature.googlephotos.session.GooglePhotosMapSessionTracker
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.math.abs

internal class GooglePhotosCameraUpdateHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
    private val diagnosticsPolicy: GooglePhotosDiagnosticsPolicy,
    private val currentLocationCameraScope: CurrentLocationCameraUpdateScope,
) {
    private val engine = context.engine
    private val coordinatePolicy = CameraUpdateCoordinatePolicy()
    private lateinit var coordinateAccessors: CoordinateAccessors

    fun install(resolver: GooglePhotosTargetResolver) {
        val coordinateClass = resolver.resolve(GooglePhotosTarget.LAT_LNG).targetClass
        coordinateAccessors = CoordinateAccessorResolver.resolve(coordinateClass)
            ?: error("LatLng accessors are ambiguous")
        val factoryClass = resolver.resolve(GooglePhotosTarget.CAMERA_UPDATE_FACTORY).targetClass
        val methods = CameraUpdateBindingResolver(coordinateClass).resolve(factoryClass)
        check(methods.size == ExpectedFactoryMethodCount) {
            "Camera update methods are ambiguous: ${methods.size}"
        }
        resolver.bindingSelected(
            GooglePhotosTarget.CAMERA_UPDATE_FACTORY,
            "methods=${methods.joinToString { method -> method.name }}",
        )
        methods.forEach(::installProbe)
    }

    private fun installProbe(method: Method) {
        method.isAccessible = true
        engine.hook(method) { chain -> interceptCameraUpdate(chain, method) }
    }

    private fun interceptCameraUpdate(chain: HookChain, method: Method): Any? {
        val coordinateValue = chain.args.firstOrNull()
        val original = readCoordinate(coordinateValue)
        val session = sessionTracker.currentSession()?.toProbeLogSnapshot()
        val callCount = logger.cameraUpdateInvoked(
            method = method.toGenericString(),
            coordinate = original,
            session = session,
            stack = cameraUpdateStack(),
        )
        val currentLocationTarget = if (original == null || session == null) {
            false
        } else {
            currentLocationCameraScope.consumeIfMatches(session.sessionId, original)
        }
        val result = original?.let {
            coordinatePolicy.transform(
                original = it,
                sessionActive = session != null,
                currentLocationTarget = currentLocationTarget,
            )
        }
            ?: return chain.proceed()
        logger.cameraUpdateResult(callCount, session, result)
        val converted = result.converted?.takeIf { result.outcome == LocationCoordinateOutcome.CONVERTED }
            ?: return chain.proceed()
        val convertedValue = createCoordinate(converted) ?: return chain.proceed()
        val updatedArguments = chain.args.toTypedArray()
        updatedArguments[CoordinateArgumentIndex] = convertedValue
        return chain.proceed(updatedArguments)
    }

    private fun readCoordinate(value: Any?): Coordinate? {
        if (value == null) return null
        return runCatching { coordinateAccessors.read(value) }
            .onFailure { error -> logger.warning("camera_update_read_coordinate", error) }
            .getOrNull()
    }

    private fun createCoordinate(coordinate: Coordinate): Any? {
        return runCatching { coordinateAccessors.create(coordinate) }
            .onFailure { error -> logger.warning("camera_update_create_coordinate", error) }
            .getOrNull()
    }

    private fun cameraUpdateStack(): String {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return DiagnosticsDisabledValue
        return Thread.currentThread().stackTrace.asSequence()
            .filterNot { frame -> frame.className.startsWith(ModulePackagePrefix) }
            .filterNot { frame -> frame.className == VmStackClassName }
            .take(StackFrameLimit)
            .joinToString(" <- ") { frame -> "${frame.className}.${frame.methodName}:${frame.lineNumber}" }
    }

    private companion object {
        private const val CoordinateArgumentIndex = 0
        private const val ExpectedFactoryMethodCount = 2
        private const val StackFrameLimit = 16
        private const val ModulePackagePrefix = "io.github.togls.hypertweaks"
        private const val VmStackClassName = "dalvik.system.VMStack"
        private const val DiagnosticsDisabledValue = "disabled"
    }
}

internal class CameraUpdateBindingResolver(
    private val coordinateClass: Class<*>,
) {
    fun resolve(factoryClass: Class<*>): List<Method> {
        return factoryClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                (
                    method.parameterTypes.contentEquals(arrayOf(coordinateClass)) ||
                        method.parameterTypes.contentEquals(
                            arrayOf(coordinateClass, Float::class.javaPrimitiveType),
                        )
                    )
        }
    }
}

internal class CameraUpdateCoordinatePolicy(
    private val transformer: LocationCoordinateTransformer = LocationCoordinateTransformer(),
) {
    fun transform(
        original: Coordinate,
        sessionActive: Boolean,
        currentLocationTarget: Boolean = false,
    ): LocationCoordinateResult? {
        if (!sessionActive) return null
        if (currentLocationTarget) {
            return LocationCoordinateResult(
                outcome = LocationCoordinateOutcome.UNCHANGED,
                reason = "CURRENT_LOCATION_CAMERA_PASSTHROUGH",
                original = original,
                converted = original,
            )
        }
        return transformer.transform(original)
    }
}

internal class CurrentLocationCameraUpdateScope(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val timeoutMillis: Long = DefaultTimeoutMillis,
) {
    private var pendingTarget: PendingCurrentLocationCameraTarget? = null

    @Synchronized
    fun record(sessionId: Long, coordinate: Coordinate) {
        pendingTarget = PendingCurrentLocationCameraTarget(
            sessionId = sessionId,
            coordinate = coordinate,
            expiresAt = clock() + timeoutMillis,
        )
    }

    @Synchronized
    fun consumeIfMatches(sessionId: Long, coordinate: Coordinate): Boolean {
        val pending = pendingTarget ?: return false
        if (clock() > pending.expiresAt) {
            pendingTarget = null
            return false
        }
        if (pending.sessionId != sessionId || !pending.coordinate.matches(coordinate)) return false
        pendingTarget = null
        return true
    }

    private fun Coordinate.matches(other: Coordinate): Boolean {
        return abs(latitude - other.latitude) <= CoordinateTolerance &&
            abs(longitude - other.longitude) <= CoordinateTolerance
    }

    private companion object {
        private const val DefaultTimeoutMillis = 2_000L
        private const val CoordinateTolerance = 0.000001
    }
}

private data class PendingCurrentLocationCameraTarget(
    val sessionId: Long,
    val coordinate: Coordinate,
    val expiresAt: Long,
)
