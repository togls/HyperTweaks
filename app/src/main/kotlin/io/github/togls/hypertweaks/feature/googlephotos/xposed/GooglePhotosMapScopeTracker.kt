package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.os.SystemClock
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

internal enum class MapEntrySource {
    COLLECTIONS,
    OTHER,
    UNKNOWN,
}

internal data class NavigationMarker(
    val sourceActivityClassName: String,
    val createdAtElapsedRealtime: Long,
)

class GooglePhotosMapScopeTracker(
    private val logger: GooglePhotosProbeLogger,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val markerResolver = GooglePhotosMapEntryResolver()
    private val mapEntrySources = Collections.synchronizedMap(WeakHashMap<Activity, MapEntrySource>())
    private val activeMapActivity = AtomicReference(WeakReference<Activity>(null))

    fun onActivityCreated(activity: Activity) {
        bindMapEntrySource(activity)
    }

    fun onActivityResumed(activity: Activity) {
        bindMapEntrySource(activity)
    }

    fun onActivityPaused(activity: Activity) {
        if (activity.javaClass.name == GooglePhotosClassNames.MapExploreActivity) {
            clearActiveMapActivity(activity)
            return
        }

        val marker = markerResolver.recordNavigation(
            sourceActivityClassName = activity.javaClass.name,
            now = elapsedRealtime(),
        )
        logger.navigationMarkerRecorded(marker)
    }

    fun onActivityDestroyed(activity: Activity) {
        synchronized(mapEntrySources) {
            mapEntrySources.remove(activity)
        }
        clearActiveMapActivity(activity)
    }

    fun currentCollectionsMapActivity(): Activity? {
        val activity = activeMapActivity.get().get() ?: return null
        return activity.takeIf(::shouldProbeMapCoordinates)
    }

    fun shouldProbeMapCoordinates(activity: Activity): Boolean {
        if (activity.isDestroyed) {
            return false
        }

        return synchronized(mapEntrySources) {
            mapEntrySources[activity] == MapEntrySource.COLLECTIONS
        }
    }

    private fun bindMapEntrySource(activity: Activity) {
        if (activity.javaClass.name != GooglePhotosClassNames.MapExploreActivity) {
            return
        }

        var newlyBound = false
        val boundSource = synchronized(mapEntrySources) {
            val currentSource = mapEntrySources[activity]
            if (currentSource == null) {
                val source = markerResolver.consumeMapEntrySource(elapsedRealtime())
                mapEntrySources[activity] = source
                newlyBound = true
                source
            } else {
                currentSource
            }
        }

        activeMapActivity.set(WeakReference(activity))
        if (newlyBound) {
            logger.mapEntrySourceBound(activity, boundSource)
        }
        if (newlyBound && boundSource != MapEntrySource.COLLECTIONS) {
            logger.coordinateProbeSkipped(activity, boundSource)
        }
    }

    private fun clearActiveMapActivity(activity: Activity) {
        val currentActivity = activeMapActivity.get().get()
        if (currentActivity === activity) {
            activeMapActivity.set(WeakReference(null))
        }
    }
}

internal class GooglePhotosMapEntryResolver(
    private val entryWindowMillis: Long = DefaultEntryWindowMillis,
) {
    private var pendingMarker: NavigationMarker? = null

    fun recordNavigation(
        sourceActivityClassName: String,
        now: Long,
    ): NavigationMarker {
        val marker = NavigationMarker(
            sourceActivityClassName = sourceActivityClassName,
            createdAtElapsedRealtime = now,
        )
        pendingMarker = marker
        return marker
    }

    fun consumeMapEntrySource(now: Long): MapEntrySource {
        val marker = pendingMarker ?: return MapEntrySource.UNKNOWN
        pendingMarker = null

        val elapsedMillis = now - marker.createdAtElapsedRealtime
        if (elapsedMillis !in 0..entryWindowMillis) {
            return MapEntrySource.UNKNOWN
        }

        return if (marker.sourceActivityClassName == GooglePhotosClassNames.CollectionsActivity) {
            MapEntrySource.COLLECTIONS
        } else {
            MapEntrySource.OTHER
        }
    }

    private companion object {
        private const val DefaultEntryWindowMillis = 3_000L
    }
}
