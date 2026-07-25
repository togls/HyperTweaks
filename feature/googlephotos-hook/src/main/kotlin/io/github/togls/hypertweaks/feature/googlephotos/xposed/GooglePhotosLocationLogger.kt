package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.logging.GooglePhotosDiagnosticsPolicy
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallResult
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosInstallTarget
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveDiagnostic
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveOutcome
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveStage
import io.github.togls.hypertweaks.feature.googlephotos.session.GooglePhotosMapSession
import io.github.togls.hypertweaks.feature.googlephotos.session.MapSessionRejectionReason
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
    private val diagnosticsPolicy: GooglePhotosDiagnosticsPolicy,
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
    private val heatmapCallCount = AtomicInteger()

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

    fun installTargetSkipped(target: GooglePhotosInstallTarget, reason: String) {
        log.info(
            event = "hook.install.skipped",
            message = "GooglePhotosLocation: target skipped",
            fields = mapOf(
                "subtarget" to target.logName,
                "reason" to reason,
            ),
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
                "heatmapIndex" to result.installed(GooglePhotosInstallTarget.HEATMAP_INDEX),
                "s2Query" to result.installed(GooglePhotosInstallTarget.S2_QUERY),
                "mapView" to result.installed(GooglePhotosInstallTarget.MAP_VIEW),
                "lifecycle" to result.installed(GooglePhotosInstallTarget.LIFECYCLE),
            ).toLogFields(),
        )
    }

    fun resolveDiagnostic(diagnostic: ResolveDiagnostic) {
        val event = when (diagnostic.stage) {
            ResolveStage.STARTED -> "target.resolve.started"
            ResolveStage.COMPLETED -> "target.resolve.succeeded"
            ResolveStage.FAILED -> "target.resolve.failed"
            else -> "target.resolve.candidate"
        }
        val fields = arrayOf(
            "subtarget" to diagnostic.target,
            "reason" to (diagnostic.detail ?: diagnostic.outcome.name),
            "stage" to diagnostic.stage,
            "className" to diagnostic.className,
            "classLoaderSource" to diagnostic.classLoaderSource,
        ).toLogFields()
        if (diagnostic.stage == ResolveStage.FAILED ||
            diagnostic.outcome == ResolveOutcome.REJECTED
        ) {
            log.warn(event, "GooglePhotosTargetResolver: candidate rejected", fields = fields)
        } else {
            log.info(event, "GooglePhotosTargetResolver: resolved target", fields = fields)
        }
    }

    fun activityEvent(event: String, snapshot: ActivityLogSnapshot) {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return DiagnosticsDisabledCallCount
        val sessionKey = session?.sessionId ?: InactiveSessionKey
        val callCount = markerCallCounts.computeIfAbsent(sessionKey) { AtomicInteger() }.incrementAndGet()
        if (shouldLogProbe(callCount, MarkerDetailedCallLimit)) {
            log.debug(
                event = "hook.callback.entered",
                message = "GooglePhotosMarker: invoked",
                fields = arrayOf(
                    "subtarget" to "marker",
                    "reason" to "CALLBACK_ENTERED",
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) {
            if (result.outcome == MarkerConversionOutcome.FAILED) warning("marker_conversion", result.failure)
            return
        }
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
            log.warn(
                "hook.callback.failed",
                message,
                result.failure,
                callbackFields("marker", result.reason, fields.toLogFields()),
            )
        } else {
            log.debug(
                markerCallbackEvent(result.outcome),
                message,
                fields = callbackFields("marker", result.reason, fields.toLogFields()),
            )
        }
    }

    fun heatmapInvoked(
        method: String,
        receiverClass: String?,
        itemCount: Int?,
        session: ProbeSessionLogSnapshot?,
    ): Int {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return DiagnosticsDisabledCallCount
        val callCount = heatmapCallCount.incrementAndGet()
        if (!shouldLogProbe(callCount, HeatmapDetailedCallLimit)) return callCount
        log.debug(
            event = "hook.callback.entered",
            message = "GooglePhotosHeatmap: invoked",
            fields = arrayOf(
                "subtarget" to "s2_builder",
                "reason" to "CALLBACK_ENTERED",
                "callCount" to callCount,
                "method" to method,
                "receiverClass" to receiverClass,
                "itemCount" to itemCount,
                "sessionId" to session?.sessionId,
                "sessionActive" to (session != null),
                "hostActivity" to session?.hostActivity,
                "thread" to Thread.currentThread().name,
            ).toLogFields(),
        )
        return callCount
    }

    fun heatmapResult(
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: HeatmapConversionResult,
    ) {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) {
            if (result.outcome == HeatmapConversionOutcome.FAILED) warning("heatmap_conversion", result.failure)
            return
        }
        if (!shouldLogProbe(callCount, HeatmapDetailedCallLimit)) return
        val fields = arrayOf(
            "callCount" to callCount,
            "sessionId" to session?.sessionId,
            "sessionActive" to (session != null),
            "reason" to result.reason,
            "validCount" to result.batchResult.validCount,
            "mainlandCount" to result.batchResult.mainlandCount,
            "convertedCount" to result.batchResult.convertedCount,
        ).toLogFields()
        if (result.outcome == HeatmapConversionOutcome.FAILED) {
            log.warn(
                "hook.callback.failed",
                "GooglePhotosHeatmap: failed",
                result.failure,
                callbackFields("s2_builder", result.reason, fields),
            )
        } else {
            log.debug(
                heatmapCallbackEvent(result.outcome),
                "GooglePhotosHeatmap: completed",
                fields = callbackFields("s2_builder", result.reason, fields),
            )
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return DiagnosticsDisabledCallCount
        val callCount = s2QueryCallCount.incrementAndGet()
        if (!shouldLogProbe(callCount, S2QueryDetailedCallLimit)) return callCount
        log.debug(
            event = "hook.callback.entered",
            message = "GooglePhotosS2Query: invoked",
            fields = callbackFields(
                "s2_query",
                if (dataBounds == null) "NO_SESSION_OR_BOUNDS" else "GCJ02_TO_WGS84_QUERY",
                s2QueryFields(callCount, bounds, dataBounds, session, thread, caller, stack),
            ),
        )
        return callCount
    }

    fun cameraUpdateInvoked(
        method: String,
        coordinate: Coordinate?,
        session: ProbeSessionLogSnapshot?,
        stack: String,
    ): Int {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return DiagnosticsDisabledCallCount
        val callCount = cameraUpdateCallCount.incrementAndGet()
        if (shouldLogProbe(callCount, CameraUpdateDetailedCallLimit)) {
            log.debug(
                event = "hook.callback.entered",
                message = "GooglePhotosCameraUpdate: invoked",
                fields = arrayOf(
                    "subtarget" to "camera_update",
                    "reason" to "CALLBACK_ENTERED",
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) {
            if (result.outcome == LocationCoordinateOutcome.FAILED) warning("camera_update", result.failure)
            return
        }
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
            log.warn(
                "hook.callback.failed",
                "GooglePhotosCameraUpdate: failed",
                result.failure,
                callbackFields("camera_update", result.reason, fields),
            )
        } else {
            log.debug(
                locationCallbackEvent(result.outcome),
                "GooglePhotosCameraUpdate: ${result.outcome}",
                fields = callbackFields("camera_update", result.reason, fields),
            )
        }
    }

    fun previewMarkerAnimationInvoked(
        constructor: String,
        target: Coordinate,
        session: ProbeSessionLogSnapshot?,
    ): Int {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return DiagnosticsDisabledCallCount
        val callCount = previewMarkerAnimationCallCount.incrementAndGet()
        if (!shouldLogProbe(callCount, PreviewMarkerAnimationDetailedCallLimit)) return callCount
        log.debug(
            event = "hook.callback.entered",
            message = "GooglePhotosPreviewMarkerAnimation: invoked",
            fields = arrayOf(
                "subtarget" to "marker_animation",
                "reason" to "CALLBACK_ENTERED",
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

    fun initialPreviewSelectionReselected(session: ProbeSessionLogSnapshot) {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
        log.info(
            event = "hook.callback.transformed",
            message = "GooglePhotosInitialPreviewSelection: reselected",
            fields = mapOf(
                "sessionId" to session.sessionId.toString(),
                "hostActivity" to session.hostActivity,
                "reason" to "INITIAL_SELECTION_RESELECTED",
            ),
        )
    }

    fun previewMarkerAnimationResult(
        callCount: Int,
        session: ProbeSessionLogSnapshot?,
        result: LocationCoordinateResult,
    ) {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) {
            if (result.outcome == LocationCoordinateOutcome.FAILED) {
                warning("preview_marker_animation", result.failure)
            }
            return
        }
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
            log.warn(
                "hook.callback.failed",
                "GooglePhotosPreviewMarkerAnimation: failed",
                result.failure,
                callbackFields("marker_animation", result.reason, fields),
            )
        } else {
            log.debug(
                locationCallbackEvent(result.outcome),
                "GooglePhotosPreviewMarkerAnimation: ${result.outcome}",
                fields = callbackFields("marker_animation", result.reason, fields),
            )
        }
    }

    fun s2QueryCompleted(callCount: Int, resultHandle: Long?) {
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        if (!diagnosticsPolicy.highFrequencyProbesEnabled) return
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
        return value?.let { String.format(Locale.US, "%.4f", it) }
    }

    private fun callbackFields(
        subtarget: String,
        reason: String,
        fields: Map<String, String>,
    ): Map<String, String> {
        return fields + mapOf("subtarget" to subtarget, "reason" to reason)
    }

    private fun markerCallbackEvent(outcome: MarkerConversionOutcome): String {
        return when (outcome) {
            MarkerConversionOutcome.CONVERTED -> "hook.callback.transformed"
            MarkerConversionOutcome.SKIPPED,
            MarkerConversionOutcome.UNCHANGED,
            -> "hook.callback.bypassed"
            MarkerConversionOutcome.FAILED -> "hook.callback.failed"
        }
    }

    private fun heatmapCallbackEvent(outcome: HeatmapConversionOutcome): String {
        return when (outcome) {
            HeatmapConversionOutcome.CONVERTED -> "hook.callback.transformed"
            HeatmapConversionOutcome.SKIPPED -> "hook.callback.bypassed"
            HeatmapConversionOutcome.FAILED -> "hook.callback.failed"
        }
    }

    private fun locationCallbackEvent(outcome: LocationCoordinateOutcome): String {
        return when (outcome) {
            LocationCoordinateOutcome.CONVERTED -> "hook.callback.transformed"
            LocationCoordinateOutcome.UNCHANGED -> "hook.callback.bypassed"
            LocationCoordinateOutcome.FAILED -> "hook.callback.failed"
        }
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
        private const val DiagnosticsDisabledCallCount = 0
        private const val MaximumLogsPerErrorType = 3
        private const val EventDetailedCallLimit = 20
        private const val MarkerDetailedCallLimit = 10
        private const val LocationDetailedCallLimit = 10
        private const val CameraUpdateDetailedCallLimit = 10
        private const val PreviewMarkerAnimationDetailedCallLimit = 10
        private const val S2QueryDetailedCallLimit = 10
        private const val HeatmapDetailedCallLimit = 10
        private const val SummaryInterval = 250
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
