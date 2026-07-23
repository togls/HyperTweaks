package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.logging.api.Logger
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

internal class GooglePhotosLocationLogger(
    private val log: Logger,
) {
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val eventCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val markerCallCounts = ConcurrentHashMap<Long, AtomicInteger>()
    private val markerSessionStats = ConcurrentHashMap<Long, MarkerSessionStats>()
    private val locationReadCount = AtomicInteger()
    private val cameraUpdateCallCount = AtomicInteger()
    private val previewMarkerAnimationCallCount = AtomicInteger()
    private val s2QueryCallCount = AtomicInteger()
    private val s2QueryResultCount = AtomicInteger()

    fun installBegin() {
        log.info(
            event = "hook.install.started",
            message = "GooglePhotosLocation: install begin",
        )
    }

    fun installTargetBegin(target: GooglePhotosInstallTarget) {
        if (target.isStrategy) {
            log.debug(
                event = "adapter.probe.started",
                message = "GooglePhotosLocation: strategy install begin",
                fields = mapOf("strategy" to target.logName),
            )
        }
    }

    fun installTargetSuccess(target: GooglePhotosInstallTarget) {
        val message = if (target.isStrategy) {
            "GooglePhotosLocation: strategy install success"
        } else {
            "GooglePhotosLocation: ${target.logName} hook installed"
        }
        log.info(
            event = "hook.install.succeeded",
            message = message,
            fields = arrayOf(targetField(target)).toLogFields(),
        )
    }

    fun installTargetFailure(target: GooglePhotosInstallTarget, error: Throwable) {
        val message = if (target.isStrategy) {
            "GooglePhotosLocation: strategy install failed"
        } else {
            "GooglePhotosLocation: ${target.logName} hook failed"
        }
        log.warn(
            event = "hook.install.failed",
            message = message,
            throwable = error,
            fields = arrayOf(
                targetField(target),
                "errorType" to error.javaClass.name,
            ).toLogFields(),
        )
    }

    fun installCompleted(result: GooglePhotosHookInstallResult) {
        log.info(
            event = "hook.install.completed",
            message = "GooglePhotosLocation: install completed",
            fields = arrayOf(
                "marker" to result.installed(GooglePhotosInstallTarget.MARKER_API),
                "markerAnimation" to result.installed(GooglePhotosInstallTarget.MARKER_ANIMATION),
                "initialPreviewSelection" to
                    result.installed(GooglePhotosInstallTarget.INITIAL_PREVIEW_SELECTION),
                "mapLocation" to result.installed(GooglePhotosInstallTarget.MAP_LOCATION),
                "cameraUpdate" to result.installed(GooglePhotosInstallTarget.CAMERA_UPDATE),
                "s2Query" to result.installed(GooglePhotosInstallTarget.S2_QUERY),
                "mapView" to result.installed(GooglePhotosInstallTarget.MAP_VIEW),
                "lifecycle" to result.installed(GooglePhotosInstallTarget.LIFECYCLE),
            ).toLogFields(),
        )
    }

    fun activityEvent(event: String, snapshot: ActivityLogSnapshot) {
        if (!shouldLogEvent("activity_$event")) return
        log.debug(
            event = "hook.callback.completed",
            message = "GooglePhotosMapSession: activity $event",
            fields = arrayOf(
                "activityClass" to snapshot.activityClass,
                "activityIdentity" to snapshot.activityIdentity,
                "isChangingConfigurations" to snapshot.isChangingConfigurations,
                "isFinishing" to snapshot.isFinishing,
                "isDestroyed" to snapshot.isDestroyed,
                "currentResumedActivity" to snapshot.currentResumedActivity,
            ).toLogFields(),
        )
    }

    fun mapViewEvent(event: String, snapshot: MapViewLogSnapshot) {
        if (!shouldLogEvent("map_view_$event")) return
        log.debug(
            event = "hook.callback.completed",
            message = "GooglePhotosMapView: $event",
            fields = arrayOf(
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
            ).toLogFields(),
        )
    }

    fun sessionTransition(event: String, snapshot: MapSessionLogSnapshot) {
        if (!shouldLogEvent("session_evaluation")) return
        log.debug(
            event = "adapter.probe.started",
            message = "GooglePhotosMapSession: evaluation",
            fields = arrayOf(
                "event" to event,
                "sessionId" to snapshot.sessionId,
                "hostActivity" to snapshot.hostActivity,
                "hostIdentity" to snapshot.hostIdentity,
                "attachedViewCount" to snapshot.attachedViewCount,
                "currentResumedActivity" to snapshot.currentResumedActivity,
                "reason" to snapshot.reason,
            ).toLogFields(),
        )
        logSessionOutcome(snapshot)
    }

    fun markerMatcherStart(activityClass: String) {
        log.debug(
            event = "adapter.probe.started",
            message = "GooglePhotosMarker: matcher start",
            fields = mapOf("activityClass" to activityClass),
        )
    }

    fun markerMatcherCompleted(report: MapRenderMatchReport) {
        log.debug("adapter.probe.started", "GooglePhotosMarker: controller candidate", fields = mapOf("count" to report.controllerCandidateCount.toString()))
        log.debug("adapter.probe.started", "GooglePhotosMarker: facade candidate", fields = mapOf("count" to report.facadeCandidateCount.toString()))
        log.debug("adapter.probe.started", "GooglePhotosMarker: method candidate", fields = mapOf("count" to report.bindings.size.toString()))
        log.info(
            event = if (report.binding == null) "adapter.probe.rejected" else "adapter.probe.selected",
            message = "GooglePhotosMarker: matcher completed",
            fields = arrayOf(
                "controllerCount" to report.controllerCandidateCount,
                "facadeCount" to report.facadeCandidateCount,
                "methodCount" to report.bindings.size,
                "matched" to (report.binding != null),
            ).toLogFields(),
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
            log.debug(
                event = "hook.callback.started",
                message = "GooglePhotosMarker: invoked",
                fields = arrayOf(
                    "callCount" to callCount,
                    "method" to method,
                    "receiverClass" to receiverClass,
                    "sessionId" to session?.sessionId,
                    "sessionActive" to (session != null),
                    "hostActivity" to session?.hostActivity,
                    "latitude" to formatCoordinate(coordinate?.latitude),
                    "longitude" to formatCoordinate(coordinate?.longitude),
                    "thread" to Thread.currentThread().name,
                ).toLogFields(),
            )
        }
        return callCount
    }

    fun locationMatcherCompleted(report: CurrentLocationRequestMatchReport) {
        if (!shouldLogEvent("location_matcher")) return
        log.info(
            event = if (report.binding == null) "adapter.probe.rejected" else "adapter.probe.selected",
            message = "GooglePhotosMapLocation: matcher completed",
            fields = arrayOf(
                "controllerCount" to report.controllerCandidateCount,
                "methodCount" to report.methodCandidateCount,
                "matched" to (report.binding != null),
            ).toLogFields(),
        )
    }

    fun locationRequestArmed(receiverClass: String, session: ProbeSessionLogSnapshot) {
        if (!shouldLogEvent("location_request")) return
        log.debug(
            event = "hook.callback.started",
            message = "GooglePhotosMapLocation: current location request armed",
            fields = mapOf(
                "receiverClass" to receiverClass,
                "sessionId" to session.sessionId.toString(),
            ),
        )
    }

    fun locationRead(
        axis: CoordinateAxis,
        decision: MapLocationReadDecision,
        session: ProbeSessionLogSnapshot,
        result: LocationCoordinateResult,
    ) {
        val callCount = locationReadCount.incrementAndGet()
        if (!shouldLogProbe(callCount, LocationDetailedCallLimit)) return
        val fields = locationResultFields(callCount, axis, decision, session, result).toLogFields()
        if (result.outcome == LocationCoordinateOutcome.FAILED) {
            log.warn("hook.callback.failed", "GooglePhotosMapLocation: failed", result.failure, fields)
        } else {
            log.debug("hook.callback.completed", "GooglePhotosMapLocation: coordinate read", fields = fields)
        }
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
            log.warn("hook.callback.failed", message, result.failure, fields.toLogFields())
        } else {
            log.debug("hook.callback.completed", message, fields = fields.toLogFields())
        }
    }

    fun s2QueryInvoked(
        bounds: S2QueryBounds?,
        dataBounds: S2QueryBounds?,
        session: ProbeSessionLogSnapshot?,
        thread: String,
        caller: String,
        stack: String,
    ): Int {
        val callCount = s2QueryCallCount.incrementAndGet()
        if (!shouldLogProbe(callCount, S2QueryDetailedCallLimit)) return callCount
        log.debug(
            event = "hook.callback.started",
            message = "GooglePhotosS2Query: invoked",
            fields = s2QueryFields(callCount, bounds, dataBounds, session, thread, caller, stack),
        )
        return callCount
    }

    fun cameraUpdateInvoked(
        method: String,
        coordinate: Coordinate?,
        session: ProbeSessionLogSnapshot?,
        stack: String,
    ): Int {
        val callCount = cameraUpdateCallCount.incrementAndGet()
        if (shouldLogProbe(callCount, CameraUpdateDetailedCallLimit)) {
            log.debug(
                event = "hook.callback.started",
                message = "GooglePhotosCameraUpdate: invoked",
                fields = arrayOf(
                    "callCount" to callCount,
                    "method" to method,
                    "latitude" to formatCoordinate(coordinate?.latitude),
                    "longitude" to formatCoordinate(coordinate?.longitude),
                    "sessionId" to session?.sessionId,
                    "sessionActive" to (session != null),
                    "hostActivity" to session?.hostActivity,
                    "thread" to Thread.currentThread().name,
                    "stack" to stack,
                ).toLogFields(),
            )
        }
        return callCount
    }

    fun cameraUpdateResult(
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: LocationCoordinateResult,
    ) {
        if (!shouldLogProbe(callCount, CameraUpdateDetailedCallLimit)) return
        val fields = arrayOf(
            "callCount" to callCount,
            "sessionId" to session?.sessionId,
            "reason" to result.reason,
            "originalLatitude" to formatCoordinate(result.original.latitude),
            "originalLongitude" to formatCoordinate(result.original.longitude),
            "convertedLatitude" to formatCoordinate(result.converted?.latitude),
            "convertedLongitude" to formatCoordinate(result.converted?.longitude),
        ).toLogFields()
        if (result.outcome == LocationCoordinateOutcome.FAILED) {
            log.warn("hook.callback.failed", "GooglePhotosCameraUpdate: failed", result.failure, fields)
        } else {
            log.debug("hook.callback.completed", "GooglePhotosCameraUpdate: ${result.outcome}", fields = fields)
        }
    }

    fun previewMarkerAnimationInvoked(
        constructor: String,
        target: Coordinate,
        session: ProbeSessionLogSnapshot?,
    ): Int {
        val callCount = previewMarkerAnimationCallCount.incrementAndGet()
        if (!shouldLogProbe(callCount, PreviewMarkerAnimationDetailedCallLimit)) return callCount
        log.debug(
            event = "hook.callback.started",
            message = "GooglePhotosPreviewMarkerAnimation: invoked",
            fields = arrayOf(
                "callCount" to callCount,
                "constructor" to constructor,
                "targetLatitude" to formatCoordinate(target.latitude),
                "targetLongitude" to formatCoordinate(target.longitude),
                "sessionId" to session?.sessionId,
                "sessionActive" to (session != null),
                "hostActivity" to session?.hostActivity,
            ).toLogFields(),
        )
        return callCount
    }

    fun initialPreviewSelectionPreserved(session: ProbeSessionLogSnapshot) {
        log.info(
            event = "hook.callback.completed",
            message = "GooglePhotosInitialPreviewSelection: preserved",
            fields = mapOf(
                "sessionId" to session.sessionId.toString(),
                "hostActivity" to session.hostActivity,
                "reason" to "INITIAL_BOUNDS_REFRESH",
            ),
        )
    }

    fun previewMarkerAnimationResult(
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: LocationCoordinateResult,
    ) {
        if (!shouldLogProbe(callCount, PreviewMarkerAnimationDetailedCallLimit)) return
        val fields = arrayOf(
            "callCount" to callCount,
            "sessionId" to session?.sessionId,
            "reason" to result.reason,
            "originalLatitude" to formatCoordinate(result.original.latitude),
            "originalLongitude" to formatCoordinate(result.original.longitude),
            "convertedLatitude" to formatCoordinate(result.converted?.latitude),
            "convertedLongitude" to formatCoordinate(result.converted?.longitude),
        ).toLogFields()
        if (result.outcome == LocationCoordinateOutcome.FAILED) {
            log.warn("hook.callback.failed", "GooglePhotosPreviewMarkerAnimation: failed", result.failure, fields)
        } else {
            log.debug(
                "hook.callback.completed",
                "GooglePhotosPreviewMarkerAnimation: ${result.outcome}",
                fields = fields,
            )
        }
    }

    fun s2QueryCompleted(callCount: Int, resultHandle: Long?) {
        if (!shouldLogProbe(callCount, S2QueryDetailedCallLimit)) return
        log.debug(
            event = "hook.callback.completed",
            message = "GooglePhotosS2Query: completed",
            fields = mapOf(
                "callCount" to callCount.toString(),
                "resultHandle" to formatHandle(resultHandle),
            ),
        )
    }

    fun s2QueryResultCount(resultHandle: Long?, itemCount: Int?) {
        val callCount = s2QueryResultCount.incrementAndGet()
        if (!shouldLogProbe(callCount, S2QueryDetailedCallLimit)) return
        log.debug(
            event = "hook.callback.completed",
            message = "GooglePhotosS2Query: result count",
            fields = mapOf(
                "callCount" to callCount.toString(),
                "resultHandle" to formatHandle(resultHandle),
                "itemCount" to itemCount.toString(),
            ),
        )
    }

    fun warning(operation: String, error: Throwable? = null) {
        val errorType = error?.javaClass?.name ?: "unknown"
        val key = "$operation:$errorType"
        val count = errorCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        if (count > MaximumLogsPerErrorType) return
        log.warn(
            event = "hook.callback.failed",
            message = "GooglePhotosLocation: operation failed",
            throwable = error,
            fields = mapOf(
                "operation" to operation,
                "errorType" to errorType,
            ),
        )
    }

    private fun logSessionOutcome(snapshot: MapSessionLogSnapshot) {
        val event = when {
            snapshot.activated -> "activated"
            snapshot.deactivated -> "deactivated"
            snapshot.reason != null -> "rejected"
            else -> return
        }
        log.info(
            event = if (snapshot.reason == null) "adapter.probe.selected" else "adapter.probe.rejected",
            message = "GooglePhotosMapSession: $event",
            fields = arrayOf(
                "sessionId" to snapshot.sessionId,
                "hostActivity" to snapshot.hostActivity,
                "hostIdentity" to snapshot.hostIdentity,
                "reason" to snapshot.reason,
            ).toLogFields(),
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

    private fun locationResultFields(
        callCount: Int,
        axis: CoordinateAxis,
        decision: MapLocationReadDecision,
        session: ProbeSessionLogSnapshot,
        result: LocationCoordinateResult,
    ): Array<Pair<String, Any?>> {
        return arrayOf(
            "callCount" to callCount,
            "axis" to axis,
            "source" to decision.source,
            "callerClass" to decision.callerClass,
            "sessionId" to session.sessionId,
            "reason" to result.reason,
            "originalLatitude" to formatCoordinate(result.original.latitude),
            "originalLongitude" to formatCoordinate(result.original.longitude),
            "convertedLatitude" to formatCoordinate(result.converted?.latitude),
            "convertedLongitude" to formatCoordinate(result.converted?.longitude),
        )
    }

    private fun targetField(target: GooglePhotosInstallTarget): Pair<String, Any?> {
        return if (target.isStrategy) "strategy" to target.logName else "component" to target.logName
    }

    private fun s2QueryFields(
        callCount: Int,
        bounds: S2QueryBounds?,
        dataBounds: S2QueryBounds?,
        session: ProbeSessionLogSnapshot?,
        thread: String,
        caller: String,
        stack: String,
    ): Map<String, String> {
        return arrayOf(
            "callCount" to callCount,
            "minimumLatitude" to formatCoordinate(bounds?.minimumLatitude),
            "minimumLongitude" to formatCoordinate(bounds?.minimumLongitude),
            "maximumLatitude" to formatCoordinate(bounds?.maximumLatitude),
            "maximumLongitude" to formatCoordinate(bounds?.maximumLongitude),
            "dataMinimumLatitude" to formatCoordinate(dataBounds?.minimumLatitude),
            "dataMinimumLongitude" to formatCoordinate(dataBounds?.minimumLongitude),
            "dataMaximumLatitude" to formatCoordinate(dataBounds?.maximumLatitude),
            "dataMaximumLongitude" to formatCoordinate(dataBounds?.maximumLongitude),
            "sessionId" to session?.sessionId,
            "sessionActive" to (session != null),
            "hostActivity" to session?.hostActivity,
            "thread" to thread,
            "caller" to caller,
            "stack" to stack,
        ).toLogFields()
    }

    private fun formatHandle(value: Long?): String {
        return value?.let { java.lang.Long.toUnsignedString(it, 16) } ?: "null"
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

    private fun Array<out Pair<String, Any?>>.toLogFields(): Map<String, String> {
        return associate { (key, value) ->
            key to when (value) {
                null -> "null"
                is Enum<*> -> value.name
                else -> value.toString()
            }
        }
    }

    private companion object {
        private const val InactiveSessionKey = -1L
        private const val MaximumLogsPerErrorType = 3
        private const val EventDetailedCallLimit = 100
        private const val MarkerDetailedCallLimit = 20
        private const val LocationDetailedCallLimit = 20
        private const val CameraUpdateDetailedCallLimit = 50
        private const val PreviewMarkerAnimationDetailedCallLimit = 50
        private const val S2QueryDetailedCallLimit = 50
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
