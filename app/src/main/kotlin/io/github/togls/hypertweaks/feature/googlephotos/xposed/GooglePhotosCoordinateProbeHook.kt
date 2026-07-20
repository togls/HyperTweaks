package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Constructor
import java.util.Collections
import java.util.WeakHashMap

class GooglePhotosCoordinateProbeHook(
    context: HookContext,
    private val logger: GooglePhotosProbeLogger,
    private val mapScopeTracker: GooglePhotosMapScopeTracker,
) {
    private val module = context.module
    private val samplingLimiter = CoordinateSamplingLimiter()

    fun install(classLoader: ClassLoader) {
        installLatLngConstructorProbe(classLoader)
    }

    fun onActivityDestroyed(activity: Activity) {
        samplingLimiter.remove(activity)
    }

    private fun installLatLngConstructorProbe(classLoader: ClassLoader) {
        val candidate = CoordinateCandidate.GoogleMapsLatLngConstructor
        val constructor = findLatLngConstructor(classLoader, candidate) ?: return

        runCatching {
            module.hook(constructor)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    probeLatLngConstruction(candidate, chain.args)
                    chain.proceed()
                }
        }.onSuccess {
            logger.coordinateCandidateInstalled(candidate)
        }.onFailure { error ->
            logger.coordinateCandidateUnavailable(candidate, error)
        }
    }

    private fun findLatLngConstructor(
        classLoader: ClassLoader,
        candidate: CoordinateCandidate,
    ): Constructor<*>? {
        val latLngClass = runCatching {
            classLoader.loadClass(candidate.ownerClassName)
        }.onFailure {
            logger.coordinateCandidateUnavailable(candidate)
        }.getOrNull() ?: return null

        return runCatching {
            latLngClass.getDeclaredConstructor(
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            logger.coordinateCandidateUnavailable(candidate, error)
        }.getOrNull()
    }

    private fun probeLatLngConstruction(
        candidate: CoordinateCandidate,
        arguments: List<Any?>,
    ) {
        val activity = mapScopeTracker.currentCollectionsMapActivity() ?: return
        val latitude = arguments.numberAt(candidate.latitudeArgumentIndex) ?: return
        val longitude = arguments.numberAt(candidate.longitudeArgumentIndex) ?: return

        when (samplingLimiter.acquire(activity, candidate, latitude, longitude)) {
            CoordinateLogDecision.LOG -> logger.coordinateObserved(
                activity = activity,
                candidate = candidate,
                latitude = latitude,
                longitude = longitude,
                stack = filteredStackTrace(),
            )

            CoordinateLogDecision.SUPPRESS_AND_REPORT -> logger.coordinateProbeSuppressed(
                activity,
                candidate,
            )

            CoordinateLogDecision.SKIP -> Unit
        }
    }

    private fun List<Any?>.numberAt(index: Int): Double? {
        return (getOrNull(index) as? Number)?.toDouble()
    }

    private fun filteredStackTrace(): List<String> {
        return Throwable().stackTrace
            .asSequence()
            .filter { frame ->
                frame.className.startsWith(GooglePhotosClassNames.PackageName) ||
                    frame.className.startsWith(GoogleMapsPackagePrefix)
            }
            .take(MaxStackFrames)
            .map { frame ->
                frame.className + "#" + frame.methodName + ":" + frame.lineNumber
            }
            .toList()
    }

    private companion object {
        private const val GoogleMapsPackagePrefix = "com.google.android.gms.maps"
        private const val MaxStackFrames = 10
    }
}

internal data class CoordinateCandidate(
    val ownerClassName: String,
    val signature: String,
    val latitudeArgumentIndex: Int,
    val longitudeArgumentIndex: Int,
) {
    companion object {
        val GoogleMapsLatLngConstructor = CoordinateCandidate(
            ownerClassName = "com.google.android.gms.maps.model.LatLng",
            signature = "com.google.android.gms.maps.model.LatLng#<init>(double,double)",
            latitudeArgumentIndex = 0,
            longitudeArgumentIndex = 1,
        )
    }
}

private class CoordinateSamplingLimiter {
    private val activityStates = Collections.synchronizedMap(WeakHashMap<Activity, ActivitySamplingState>())

    fun acquire(
        activity: Activity,
        candidate: CoordinateCandidate,
        latitude: Double,
        longitude: Double,
    ): CoordinateLogDecision {
        return synchronized(activityStates) {
            val state = activityStates.getOrPut(activity) { ActivitySamplingState() }
            state.acquire(candidate, CoordinateKey(latitude.toBits(), longitude.toBits()))
        }
    }

    fun remove(activity: Activity) {
        synchronized(activityStates) {
            activityStates.remove(activity)
        }
    }
}

private class ActivitySamplingState {
    private val candidateStates = mutableMapOf<String, CandidateSamplingState>()

    fun acquire(
        candidate: CoordinateCandidate,
        coordinate: CoordinateKey,
    ): CoordinateLogDecision {
        val state = candidateStates.getOrPut(candidate.signature) { CandidateSamplingState() }
        return state.acquire(coordinate)
    }
}

private class CandidateSamplingState {
    private val observedCoordinates = mutableSetOf<CoordinateKey>()
    private var loggedCount = 0
    private var suppressionReported = false

    fun acquire(coordinate: CoordinateKey): CoordinateLogDecision {
        if (!observedCoordinates.add(coordinate)) {
            return CoordinateLogDecision.SKIP
        }

        if (loggedCount < MaxLogsPerCandidate) {
            loggedCount += 1
            return CoordinateLogDecision.LOG
        }

        if (!suppressionReported) {
            suppressionReported = true
            return CoordinateLogDecision.SUPPRESS_AND_REPORT
        }

        return CoordinateLogDecision.SKIP
    }

    private companion object {
        private const val MaxLogsPerCandidate = 10
    }
}

private data class CoordinateKey(
    val latitudeBits: Long,
    val longitudeBits: Long,
)

private enum class CoordinateLogDecision {
    LOG,
    SUPPRESS_AND_REPORT,
    SKIP,
}
