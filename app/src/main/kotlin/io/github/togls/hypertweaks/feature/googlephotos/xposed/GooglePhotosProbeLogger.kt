package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.app.Application
import io.github.togls.hypertweaks.core.xposed.util.HookLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class GooglePhotosProbeLogger(
    private val log: HookLog,
) {
    private val eventCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val discoveredFragmentClassNames = ConcurrentHashMap.newKeySet<String>()

    fun activityEvent(
        event: String,
        activity: Activity,
    ) {
        log.i(
            message = "GooglePhotosProbe: activity event",
            "event" to event,
            "package" to GooglePhotosClassNames.PackageName,
            "process" to Application.getProcessName(),
            "activity" to activity.javaClass.name,
        )
    }

    fun fragmentEvent(
        event: String,
        host: Activity,
        fragmentClassName: String,
    ) {
        logFragmentDiscovery(host, fragmentClassName)
        if (!shouldLog("fragment:" + event + ":" + host.javaClass.name + ":" + fragmentClassName)) {
            return
        }

        log.i(
            message = "GooglePhotosProbe: fragment event",
            "event" to event,
            "host" to host.javaClass.name,
            "fragment" to fragmentClassName,
        )
    }

    fun fragmentVisibility(
        host: Activity,
        fragmentClassName: String,
        event: String,
        propertyName: String,
        visibleValue: Boolean,
    ) {
        logFragmentDiscovery(host, fragmentClassName)
        if (!shouldLog(
                "visibility:" + event + ":" + host.javaClass.name + ":" +
                    fragmentClassName + ":" + visibleValue,
            )
        ) {
            return
        }

        log.i(
            message = "GooglePhotosProbe: fragment visibility",
            "event" to event,
            "host" to host.javaClass.name,
            "fragment" to fragmentClassName,
            propertyName to visibleValue,
        )
    }

    fun viewSummary(
        host: Activity,
        summary: GooglePhotosViewSummary,
    ) {
        log.i(
            message = "GooglePhotosProbe: view summary",
            "host" to host.javaClass.name,
            "composeView" to (summary.composeViewCount > 0),
            "mapView" to (summary.mapViewCount > 0),
            "surfaceView" to (summary.surfaceViewCount > 0),
            "textureView" to (summary.textureViewCount > 0),
            "customMapViews" to summary.customMapViewClassNames,
            "customMapViewCounts" to summary.customMapViewCounts,
            "viewCounts" to summary.highlightedViewCounts,
        )
    }

    fun viewTree(
        host: Activity,
        node: GooglePhotosViewTreeNode,
    ) {
        log.i(
            message = "GooglePhotosProbe: view tree",
            "host" to host.javaClass.name,
            "depth" to node.depth,
            "class" to node.className,
            "parent" to node.parentClassName,
        )
    }

    fun viewScanCompleted(
        host: Activity,
        summary: GooglePhotosViewSummary,
        durationMillis: Long,
    ) {
        log.i(
            message = "GooglePhotosProbe: view scan",
            "host" to host.javaClass.name,
            "nodes" to summary.visitedNodeCount,
            "maxDepth" to summary.reachedDepth,
            "truncated" to summary.truncated,
            "durationMs" to durationMillis,
        )
    }

    fun pageChanged(
        from: GooglePhotosPage,
        to: GooglePhotosPage,
        host: Activity,
        reason: String,
    ) {
        log.i(
            message = "GooglePhotosProbe: page changed",
            "from" to from,
            "to" to to,
            "host" to host.javaClass.name,
            "reason" to reason,
        )
    }

    internal fun navigationMarkerRecorded(marker: NavigationMarker) {
        log.i(
            message = "GooglePhotosMapScope: navigation marker recorded",
            "sourceActivity" to marker.sourceActivityClassName,
            "createdAtElapsedRealtime" to marker.createdAtElapsedRealtime,
        )
    }

    internal fun mapEntrySourceBound(
        activity: Activity,
        source: MapEntrySource,
    ) {
        log.i(
            message = "GooglePhotosMapScope: map entry source bound",
            "activity" to activity.javaClass.name,
            "source" to source,
        )
    }

    internal fun coordinateCandidateInstalled(candidate: CoordinateCandidate) {
        log.i(
            message = "GooglePhotosCoordinateProbe: candidate installed",
            "signature" to candidate.signature,
            "latitudeArg" to candidate.latitudeArgumentIndex,
            "longitudeArg" to candidate.longitudeArgumentIndex,
        )
    }

    internal fun coordinateCandidateUnavailable(
        candidate: CoordinateCandidate,
        error: Throwable? = null,
    ) {
        log.w(
            message = "GooglePhotosCoordinateProbe: candidate unavailable",
            error = error,
            "signature" to candidate.signature,
        )
    }

    internal fun coordinateProbeSkipped(
        activity: Activity,
        source: MapEntrySource,
    ) {
        log.i(
            message = "GooglePhotosCoordinateProbe: skipped outside Collections scope",
            "activity" to activity.javaClass.name,
            "source" to source,
        )
    }

    internal fun coordinateObserved(
        activity: Activity,
        candidate: CoordinateCandidate,
        latitude: Double,
        longitude: Double,
        stack: List<String>,
    ) {
        log.i(
            message = "GooglePhotosCoordinateProbe: coordinate observed",
            "activity" to activity.javaClass.name,
            "source" to MapEntrySource.COLLECTIONS,
            "signature" to candidate.signature,
            "latitude" to latitude,
            "longitude" to longitude,
            "stack" to stack,
        )
    }

    internal fun coordinateProbeSuppressed(
        activity: Activity,
        candidate: CoordinateCandidate,
    ) {
        log.i(
            message = "GooglePhotosCoordinateProbe: candidate sample limit reached",
            "activity" to activity.javaClass.name,
            "signature" to candidate.signature,
        )
    }

    fun warning(
        message: String,
        error: Throwable? = null,
    ) {
        log.w(message, error)
    }

    private fun logFragmentDiscovery(
        host: Activity,
        fragmentClassName: String,
    ) {
        if (!discoveredFragmentClassNames.add(fragmentClassName)) {
            return
        }

        log.i(
            message = "GooglePhotosProbe: fragment discovered",
            "host" to host.javaClass.name,
            "fragment" to fragmentClassName,
        )
    }

    private fun shouldLog(key: String): Boolean {
        val count = eventCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        return count <= MaxRepeatedEventLogs
    }

    private companion object {
        private const val MaxRepeatedEventLogs = 3
    }
}
