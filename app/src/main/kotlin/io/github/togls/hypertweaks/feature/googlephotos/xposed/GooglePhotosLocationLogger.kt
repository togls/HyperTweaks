package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.util.HookLog
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class ActivityLogSnapshot(
    val activityClass: String,
    val activityIdentity: String,
    val isChangingConfigurations: Boolean,
    val isFinishing: Boolean,
    val isDestroyed: Boolean,
    val currentResumedActivity: String?,
)

internal data class MapViewLogSnapshot(
    val viewClass: String,
    val viewIdentity: String,
    val hostActivity: String?,
    val hostIdentity: String?,
    val parentClass: String?,
    val rootViewClass: String?,
    val windowVisibility: Int,
    val width: Int,
    val height: Int,
    val isShown: Boolean,
    val thread: String,
    val parentPath: String,
)

internal data class MapSessionLogSnapshot(
    val sessionId: Long?,
    val hostActivity: String?,
    val hostIdentity: String?,
    val attachedViewCount: Int,
    val currentResumedActivity: String?,
    val activated: Boolean,
    val deactivated: Boolean,
    val reason: MapSessionRejectionReason?,
)

internal data class ProbeSessionLogSnapshot(
    val sessionId: Long,
    val hostActivity: String,
    val hostIdentity: String,
)

internal data class HeatmapInvocationLogSnapshot(
    val method: String,
    val receiverClass: String?,
    val argumentTypes: String,
    val latitudeArraySize: Int?,
    val longitudeArraySize: Int?,
    val session: ProbeSessionLogSnapshot?,
    val thread: String,
    val elapsedRealtime: Long,
    val filteredStack: String?,
)

internal data class HeatmapArrayLogSnapshot(
    val latSize: Int?,
    val lngSize: Int?,
    val sizeMatched: Boolean,
    val validCount: Int,
    val chinaCount: Int,
    val convertedCount: Int,
    val firstSample: String?,
    val middleSample: String?,
    val lastSample: String?,
)

internal class GooglePhotosLocationLogger(
    private val log: HookLog,
) {
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val eventCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val markerCallCounts = ConcurrentHashMap<Long, AtomicInteger>()
    private val markerSessionStats = ConcurrentHashMap<Long, MarkerSessionStats>()
    private val heatmapCallCount = AtomicInteger()

    fun installBegin() {
        log.i("GooglePhotosLocation: install begin")
    }

    fun installTargetBegin(target: GooglePhotosInstallTarget) {
        if (target.isStrategy) {
            log.i("GooglePhotosLocation: strategy install begin", "strategy" to target.logName)
        }
    }

    fun installTargetSuccess(target: GooglePhotosInstallTarget) {
        val message = if (target.isStrategy) {
            "GooglePhotosLocation: strategy install success"
        } else {
            "GooglePhotosLocation: ${target.logName} hook installed"
        }
        log.i(message, targetField(target))
    }

    fun installTargetFailure(target: GooglePhotosInstallTarget, error: Throwable) {
        val message = if (target.isStrategy) {
            "GooglePhotosLocation: strategy install failed"
        } else {
            "GooglePhotosLocation: ${target.logName} hook failed"
        }
        log.w(
            message = message,
            error = error,
            targetField(target),
            "errorType" to error.javaClass.name,
        )
    }

    fun installCompleted(result: GooglePhotosHookInstallResult) {
        log.i(
            message = "GooglePhotosLocation: install completed",
            "marker" to result.installed(GooglePhotosInstallTarget.MARKER_API),
            "s2" to result.installed(GooglePhotosInstallTarget.S2_INDEX),
            "mapView" to result.installed(GooglePhotosInstallTarget.MAP_VIEW),
            "lifecycle" to result.installed(GooglePhotosInstallTarget.LIFECYCLE),
        )
    }

    fun activityEvent(event: String, snapshot: ActivityLogSnapshot) {
        if (!shouldLogEvent("activity_$event")) return
        log.i(
            message = "GooglePhotosMapSession: activity $event",
            "activityClass" to snapshot.activityClass,
            "activityIdentity" to snapshot.activityIdentity,
            "isChangingConfigurations" to snapshot.isChangingConfigurations,
            "isFinishing" to snapshot.isFinishing,
            "isDestroyed" to snapshot.isDestroyed,
            "currentResumedActivity" to snapshot.currentResumedActivity,
        )
    }

    fun mapViewEvent(event: String, snapshot: MapViewLogSnapshot) {
        if (!shouldLogEvent("map_view_$event")) return
        log.i(
            message = "GooglePhotosMapView: $event",
            "viewClass" to snapshot.viewClass,
            "viewIdentity" to snapshot.viewIdentity,
            "hostActivity" to snapshot.hostActivity,
            "hostIdentity" to snapshot.hostIdentity,
            "parentClass" to snapshot.parentClass,
            "rootViewClass" to snapshot.rootViewClass,
            "windowVisibility" to snapshot.windowVisibility,
            "width" to snapshot.width,
            "height" to snapshot.height,
            "isShown" to snapshot.isShown,
            "thread" to snapshot.thread,
            "parentPath" to snapshot.parentPath,
        )
    }

    fun sessionTransition(event: String, snapshot: MapSessionLogSnapshot) {
        if (!shouldLogEvent("session_evaluation")) return
        log.i(
            message = "GooglePhotosMapSession: evaluation",
            "event" to event,
            "sessionId" to snapshot.sessionId,
            "hostActivity" to snapshot.hostActivity,
            "hostIdentity" to snapshot.hostIdentity,
            "attachedViewCount" to snapshot.attachedViewCount,
            "currentResumedActivity" to snapshot.currentResumedActivity,
            "reason" to snapshot.reason,
        )
        logSessionOutcome(snapshot)
    }

    fun markerMatcherStart(activityClass: String) {
        log.i("GooglePhotosMarker: matcher start", "activityClass" to activityClass)
    }

    fun markerMatcherCompleted(report: MapRenderMatchReport) {
        log.i("GooglePhotosMarker: controller candidate", "count" to report.controllerCandidateCount)
        log.i("GooglePhotosMarker: facade candidate", "count" to report.facadeCandidateCount)
        log.i("GooglePhotosMarker: method candidate", "count" to report.bindings.size)
        log.i(
            message = "GooglePhotosMarker: matcher completed",
            "controllerCount" to report.controllerCandidateCount,
            "facadeCount" to report.facadeCandidateCount,
            "methodCount" to report.bindings.size,
            "matched" to (report.binding != null),
        )
    }

    fun markerInvoked(
        method: String,
        receiverClass: String?,
        session: ProbeSessionLogSnapshot?,
        coordinate: Coordinate?,
    ): Int {
        val sessionKey = session?.sessionId ?: InactiveSessionKey
        val callCount = markerCallCounts.computeIfAbsent(sessionKey) { AtomicInteger() }.incrementAndGet()
        if (shouldLogProbe(callCount, MarkerDetailedCallLimit)) {
            log.i(
                message = "GooglePhotosMarker: invoked",
                "callCount" to callCount,
                "method" to method,
                "receiverClass" to receiverClass,
                "sessionId" to session?.sessionId,
                "sessionActive" to (session != null),
                "hostActivity" to session?.hostActivity,
                "latitude" to formatCoordinate(coordinate?.latitude),
                "longitude" to formatCoordinate(coordinate?.longitude),
                "thread" to Thread.currentThread().name,
            )
        }
        return callCount
    }

    fun markerResult(
        event: String,
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: MarkerConversionResult,
    ) {
        val sessionKey = session?.sessionId ?: InactiveSessionKey
        val stats = markerSessionStats.computeIfAbsent(sessionKey) { MarkerSessionStats() }
        stats.record(result.outcome)
        if (!shouldLogProbe(callCount, MarkerDetailedCallLimit)) return
        val message = "GooglePhotosMarker: $event"
        val fields = if (callCount <= MarkerDetailedCallLimit) {
            markerResultFields(callCount, session, result)
        } else {
            markerSummaryFields(callCount, session, result.reason, stats)
        }
        if (result.outcome == MarkerConversionOutcome.FAILED) {
            log.w(message, result.failure, *fields)
        } else {
            log.i(message, *fields)
        }
    }

    fun heatmapInvoked(snapshot: HeatmapInvocationLogSnapshot): Int {
        val callCount = heatmapCallCount.incrementAndGet()
        if (!shouldLogProbe(callCount, HeatmapDetailedCallLimit)) return callCount
        log.i(
            message = "GooglePhotosHeatmap: invoked",
            "callCount" to callCount,
            "method" to snapshot.method,
            "receiverClass" to snapshot.receiverClass,
            "argumentTypes" to snapshot.argumentTypes,
            "latitudeArraySize" to snapshot.latitudeArraySize,
            "longitudeArraySize" to snapshot.longitudeArraySize,
            "sessionId" to snapshot.session?.sessionId,
            "sessionActive" to (snapshot.session != null),
            "hostActivity" to snapshot.session?.hostActivity,
            "thread" to snapshot.thread,
            "elapsedRealtime" to snapshot.elapsedRealtime,
            "stack" to snapshot.filteredStack,
        )
        return callCount
    }

    fun heatmapArray(callCount: Int, snapshot: HeatmapArrayLogSnapshot) {
        if (!shouldLogProbe(callCount, HeatmapDetailedCallLimit)) return
        log.i(
            message = "GooglePhotosHeatmap: array",
            "latSize" to snapshot.latSize,
            "lngSize" to snapshot.lngSize,
            "sizeMatched" to snapshot.sizeMatched,
            "validCount" to snapshot.validCount,
            "chinaCount" to snapshot.chinaCount,
            "convertedCount" to snapshot.convertedCount,
            "sample[0]" to snapshot.firstSample,
            "sample[mid]" to snapshot.middleSample,
            "sample[last]" to snapshot.lastSample,
        )
    }

    fun heatmapResult(
        event: String,
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: HeatmapSessionConversionResult,
    ) {
        if (!shouldLogProbe(callCount, HeatmapDetailedCallLimit)) return
        val fields = arrayOf(
            "callCount" to callCount,
            "sessionId" to session?.sessionId,
            "sessionActive" to (session != null),
            "hostActivity" to session?.hostActivity,
            "reason" to result.reason,
            "convertedCount" to result.batchResult.convertedCount,
        )
        val message = "GooglePhotosHeatmap: $event"
        if (result.outcome == HeatmapConversionOutcome.FAILED) {
            log.w(message, result.failure, *fields)
        } else {
            log.i(message, *fields)
        }
    }

    fun warning(operation: String, error: Throwable? = null) {
        val errorType = error?.javaClass?.name ?: "unknown"
        val key = "$operation:$errorType"
        val count = errorCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        if (count > MaximumLogsPerErrorType) return
        log.w(
            message = "GooglePhotosLocation: operation failed",
            error = error,
            "operation" to operation,
            "errorType" to errorType,
        )
    }

    private fun logSessionOutcome(snapshot: MapSessionLogSnapshot) {
        val event = when {
            snapshot.activated -> "activated"
            snapshot.deactivated -> "deactivated"
            snapshot.reason != null -> "rejected"
            else -> return
        }
        log.i(
            message = "GooglePhotosMapSession: $event",
            "sessionId" to snapshot.sessionId,
            "hostActivity" to snapshot.hostActivity,
            "hostIdentity" to snapshot.hostIdentity,
            "reason" to snapshot.reason,
        )
    }

    private fun markerResultFields(
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: MarkerConversionResult,
    ): Array<Pair<String, Any?>> {
        return arrayOf(
            "callCount" to callCount,
            "sessionId" to session?.sessionId,
            "sessionActive" to (session != null),
            "hostActivity" to session?.hostActivity,
            "reason" to result.reason,
            "originalLatitude" to formatCoordinate(result.original?.latitude),
            "originalLongitude" to formatCoordinate(result.original?.longitude),
            "convertedLatitude" to formatCoordinate(result.converted?.latitude),
            "convertedLongitude" to formatCoordinate(result.converted?.longitude),
        )
    }

    private fun markerSummaryFields(
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        reason: String,
        stats: MarkerSessionStats,
    ): Array<Pair<String, Any?>> {
        return arrayOf(
            "callCount" to callCount,
            "sessionId" to session?.sessionId,
            "sessionActive" to (session != null),
            "hostActivity" to session?.hostActivity,
            "reason" to reason,
            "skippedCount" to stats.skipped.get(),
            "convertedCount" to stats.converted.get(),
            "unchangedCount" to stats.unchanged.get(),
            "failedCount" to stats.failed.get(),
        )
    }

    private fun targetField(target: GooglePhotosInstallTarget): Pair<String, Any?> {
        return if (target.isStrategy) "strategy" to target.logName else "component" to target.logName
    }

    private fun shouldLogEvent(key: String): Boolean {
        val count = eventCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        return count <= EventDetailedCallLimit || count % SummaryInterval == 0
    }

    private fun shouldLogProbe(callCount: Int, detailedCallLimit: Int): Boolean {
        return callCount <= detailedCallLimit || callCount % SummaryInterval == 0
    }

    private fun formatCoordinate(value: Double?): String? {
        return value?.let { String.format(Locale.US, "%.6f", it) }
    }

    private companion object {
        private const val InactiveSessionKey = -1L
        private const val MaximumLogsPerErrorType = 3
        private const val EventDetailedCallLimit = 100
        private const val MarkerDetailedCallLimit = 20
        private const val HeatmapDetailedCallLimit = 20
        private const val SummaryInterval = 100
    }
}

internal class MarkerSessionStats {
    val skipped = AtomicInteger()
    val converted = AtomicInteger()
    val unchanged = AtomicInteger()
    val failed = AtomicInteger()

    fun record(outcome: MarkerConversionOutcome) {
        when (outcome) {
            MarkerConversionOutcome.SKIPPED -> skipped.incrementAndGet()
            MarkerConversionOutcome.CONVERTED -> converted.incrementAndGet()
            MarkerConversionOutcome.UNCHANGED -> unchanged.incrementAndGet()
            MarkerConversionOutcome.FAILED -> failed.incrementAndGet()
        }
    }
}

internal fun <ActivityKey : Any> GooglePhotosMapSession<ActivityKey>.toProbeLogSnapshot(): ProbeSessionLogSnapshot {
    return ProbeSessionLogSnapshot(
        sessionId = sessionId,
        hostActivity = hostClassName,
        hostIdentity = Integer.toHexString(System.identityHashCode(hostActivity)),
    )
}
