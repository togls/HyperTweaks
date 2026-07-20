package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import java.util.ArrayDeque
import java.util.Collections
import java.util.LinkedHashMap
import java.util.WeakHashMap

data class GooglePhotosViewSummary(
    val composeViewCount: Int,
    val mapViewCount: Int,
    val surfaceViewCount: Int,
    val textureViewCount: Int,
    val customMapViewClassNames: List<String>,
    val customMapViewCounts: Map<String, Int>,
    val highlightedViewCounts: Map<String, Int>,
    val visitedNodeCount: Int,
    val reachedDepth: Int,
    val truncated: Boolean,
)

data class GooglePhotosViewTreeNode(
    val depth: Int,
    val className: String,
    val parentClassName: String?,
)

class GooglePhotosViewProbe(
    private val logger: GooglePhotosProbeLogger,
    private val pageTracker: GooglePhotosPageTracker,
) {
    private val scanStates = Collections.synchronizedMap(WeakHashMap<Activity, ScanState>())

    fun schedule(
        activity: Activity,
        trigger: String,
    ) {
        if (!reserveScan(activity)) {
            return
        }

        runCatching {
            activity.window.decorView.post {
                scan(activity, trigger)
            }
        }.onFailure { error ->
            logger.warning("GooglePhotosProbe: failed to schedule view scan", error)
        }
    }

    fun onActivityDestroyed(activity: Activity) {
        scanStates.remove(activity)
    }

    private fun reserveScan(activity: Activity): Boolean {
        val now = SystemClock.elapsedRealtime()
        synchronized(scanStates) {
            val state = scanStates[activity] ?: ScanState()
            if (state.scanCount >= MaxScansPerActivity || now - state.lastRequestMillis < DebounceMillis) {
                return false
            }

            state.lastRequestMillis = now
            state.scanCount += 1
            scanStates[activity] = state
            return true
        }
    }

    private fun scan(
        activity: Activity,
        trigger: String,
    ) {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        runCatching {
            val collectedSummary = collectSummary(activity.window.decorView)
            val summary = collectedSummary.asPublicSummary()
            logger.viewSummary(activity, summary)
            collectedSummary.treeNodes.forEach { node ->
                logger.viewTree(activity, node)
            }
            pageTracker.onViewObserved(activity, summary, trigger)
            val durationMillis = (SystemClock.elapsedRealtimeNanos() - startedAt) / NanosPerMillisecond
            logger.viewScanCompleted(activity, summary, durationMillis)
        }.onFailure { error ->
            logger.warning("GooglePhotosProbe: view scan failed", error)
        }
    }

    private fun collectSummary(decorView: View): CollectedViewSummary {
        val nodes = ArrayDeque<ViewNode>()
        nodes.add(ViewNode(decorView, depth = 0, parentClassName = null))

        val collector = ViewSummaryCollector()
        while (nodes.isNotEmpty() && collector.visitedNodeCount < MaxNodes) {
            val node = nodes.removeFirst()
            collector.record(node)

            val group = node.view as? ViewGroup ?: continue
            if (node.depth >= MaxDepth) {
                collector.truncated = collector.truncated || group.childCount > 0
                continue
            }

            repeat(group.childCount) { index ->
                nodes.addLast(
                    ViewNode(
                        view = group.getChildAt(index),
                        depth = node.depth + 1,
                        parentClassName = node.view.javaClass.name,
                    ),
                )
            }
        }

        collector.truncated = collector.truncated || nodes.isNotEmpty()
        return collector.build()
    }

    private data class ScanState(
        var lastRequestMillis: Long = 0,
        var scanCount: Int = 0,
    )

    private data class ViewNode(
        val view: View,
        val depth: Int,
        val parentClassName: String?,
    )

    private companion object {
        private const val DebounceMillis = 800L
        private const val MaxScansPerActivity = 10
        private const val MaxDepth = 12
        private const val MaxNodes = 500
        private const val MaxTreeNodes = 12
        private const val NanosPerMillisecond = 1_000_000L
    }

    private class ViewSummaryCollector {
        private val customMapViews = LinkedHashMap<String, Int>()
        private val highlightedViews = LinkedHashMap<String, Int>()
        private val treeNodes = mutableListOf<GooglePhotosViewTreeNode>()

        var composeViewCount = 0
        var mapViewCount = 0
        var surfaceViewCount = 0
        var textureViewCount = 0
        var visitedNodeCount = 0
        var reachedDepth = 0
        var truncated = false

        fun record(node: ViewNode) {
            visitedNodeCount += 1
            reachedDepth = maxOf(reachedDepth, node.depth)

            val className = node.view.javaClass.name
            if (!classify(className)) {
                return
            }

            highlightedViews[className] = (highlightedViews[className] ?: 0) + 1
            if (treeNodes.size < MaxTreeNodes) {
                treeNodes += GooglePhotosViewTreeNode(
                    depth = node.depth,
                    className = className,
                    parentClassName = node.parentClassName,
                )
            }
        }

        fun build(): CollectedViewSummary {
            return CollectedViewSummary(
                composeViewCount = composeViewCount,
                mapViewCount = mapViewCount,
                surfaceViewCount = surfaceViewCount,
                textureViewCount = textureViewCount,
                customMapViewClassNames = customMapViews.keys.toList(),
                customMapViewCounts = customMapViews.toMap(),
                highlightedViewCounts = highlightedViews.toMap(),
                visitedNodeCount = visitedNodeCount,
                reachedDepth = reachedDepth,
                truncated = truncated,
                treeNodes = treeNodes.toList(),
            )
        }

        private fun classify(className: String): Boolean {
            return when (className) {
                ComposeViewClassName -> {
                    composeViewCount += 1
                    true
                }

                MapViewClassName -> {
                    mapViewCount += 1
                    true
                }

                SurfaceViewClassName -> {
                    surfaceViewCount += 1
                    true
                }

                TextureViewClassName -> {
                    textureViewCount += 1
                    true
                }

                RecyclerViewClassName,
                WebViewClassName,
                -> true

                else -> recordCustomMapView(className)
            }
        }

        private fun recordCustomMapView(className: String): Boolean {
            val normalizedName = className.lowercase()
            if (MapKeywords.none { keyword -> normalizedName.contains(keyword) }) {
                return false
            }

            customMapViews[className] = (customMapViews[className] ?: 0) + 1
            return true
        }

        private companion object {
            private const val ComposeViewClassName = "androidx.compose.ui.platform.ComposeView"
            private const val MapViewClassName = "com.google.android.gms.maps.MapView"
            private const val SurfaceViewClassName = "android.view.SurfaceView"
            private const val TextureViewClassName = "android.view.TextureView"
            private const val RecyclerViewClassName = "androidx.recyclerview.widget.RecyclerView"
            private const val WebViewClassName = "android.webkit.WebView"
            private val MapKeywords = setOf(
                "map",
                "geo",
                "place",
                "location",
                "explore",
                "cluster",
            )
        }
    }
}

private data class CollectedViewSummary(
    val composeViewCount: Int,
    val mapViewCount: Int,
    val surfaceViewCount: Int,
    val textureViewCount: Int,
    val customMapViewClassNames: List<String>,
    val customMapViewCounts: Map<String, Int>,
    val highlightedViewCounts: Map<String, Int>,
    val visitedNodeCount: Int,
    val reachedDepth: Int,
    val truncated: Boolean,
    val treeNodes: List<GooglePhotosViewTreeNode>,
) {
    fun asPublicSummary(): GooglePhotosViewSummary {
        return GooglePhotosViewSummary(
            composeViewCount = composeViewCount,
            mapViewCount = mapViewCount,
            surfaceViewCount = surfaceViewCount,
            textureViewCount = textureViewCount,
            customMapViewClassNames = customMapViewClassNames,
            customMapViewCounts = customMapViewCounts,
            highlightedViewCounts = highlightedViewCounts,
            visitedNodeCount = visitedNodeCount,
            reachedDepth = reachedDepth,
            truncated = truncated,
        )
    }
}
