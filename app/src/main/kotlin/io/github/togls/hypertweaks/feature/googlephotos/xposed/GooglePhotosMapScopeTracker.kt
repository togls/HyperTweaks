package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.os.SystemClock
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal enum class MapEntrySource {
    COLLECTIONS,
    OTHER,
}

internal data class NavigationMarker(
    val source: MapEntrySource,
    val sourceActivityClassName: String,
    val createdAtElapsedRealtime: Long,
)

internal data class MapActivityScope(
    val source: MapEntrySource,
    val createdAtElapsedRealtime: Long,
)

internal class GooglePhotosMapScopeTracker(
    private val logger: GooglePhotosLocationLogger,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val entryResolver = GooglePhotosMapEntryResolver()
    private val stateMachine = GooglePhotosMapScopeStateMachine<Activity>()

    fun onActivityCreated(activity: Activity) {
        if (!activity.isMapExploreActivity()) {
            return
        }

        val now = elapsedRealtime()
        val scope = MapActivityScope(
            source = entryResolver.consumeMapEntrySource(now),
            createdAtElapsedRealtime = now,
        )
        stateMachine.bind(activity, scope)
        logger.scopeBound(scope.source)
    }

    fun onActivityResumed(activity: Activity) {
        if (!stateMachine.activate(activity)) {
            return
        }

        logger.scopeActivated()
    }

    fun onActivityPaused(activity: Activity) {
        if (activity.isMapExploreActivity()) {
            deactivate(activity)
            return
        }

        entryResolver.recordNavigation(
            sourceActivityClassName = activity.javaClass.name,
            now = elapsedRealtime(),
        )
    }

    fun onActivityDestroyed(activity: Activity) {
        if (stateMachine.remove(activity)) {
            logger.scopeDeactivated()
        }
    }

    fun currentCollectionsMapActivity(): Activity? {
        return stateMachine.currentCollectionsActivity()
            ?.takeUnless(Activity::isDestroyed)
    }

    private fun deactivate(activity: Activity) {
        if (stateMachine.deactivate(activity)) {
            logger.scopeDeactivated()
        }
    }

    private fun Activity.isMapExploreActivity(): Boolean {
        return javaClass.name == GooglePhotosClassNames.MapExploreActivity
    }
}

internal class GooglePhotosMapScopeStateMachine<ActivityKey : Any> {
    private val activityScopes = WeakHashMap<ActivityKey, MapActivityScope>()
    private var activeActivity = WeakReference<ActivityKey>(null)

    @Synchronized
    fun bind(
        activity: ActivityKey,
        scope: MapActivityScope,
    ) {
        activityScopes[activity] = scope
    }

    @Synchronized
    fun activate(activity: ActivityKey): Boolean {
        if (activityScopes[activity]?.source != MapEntrySource.COLLECTIONS) {
            activeActivity = WeakReference(null)
            return false
        }

        activeActivity = WeakReference(activity)
        return true
    }

    @Synchronized
    fun deactivate(activity: ActivityKey): Boolean {
        if (activeActivity.get() !== activity) {
            return false
        }

        activeActivity = WeakReference(null)
        return true
    }

    @Synchronized
    fun remove(activity: ActivityKey): Boolean {
        activityScopes.remove(activity)
        return deactivate(activity)
    }

    @Synchronized
    fun currentCollectionsActivity(): ActivityKey? {
        val activity = activeActivity.get() ?: return null
        return activity.takeIf {
            activityScopes[it]?.source == MapEntrySource.COLLECTIONS
        }
    }
}

internal class GooglePhotosMapEntryResolver(
    private val entryWindowMillis: Long = DefaultEntryWindowMillis,
) {
    private var pendingMarker: NavigationMarker? = null

    @Synchronized
    fun recordNavigation(
        sourceActivityClassName: String,
        now: Long,
    ): NavigationMarker {
        val source = if (sourceActivityClassName == GooglePhotosClassNames.CollectionsActivity) {
            MapEntrySource.COLLECTIONS
        } else {
            MapEntrySource.OTHER
        }
        return NavigationMarker(source, sourceActivityClassName, now).also {
            pendingMarker = it
        }
    }

    @Synchronized
    fun consumeMapEntrySource(now: Long): MapEntrySource {
        val marker = pendingMarker ?: return MapEntrySource.OTHER
        pendingMarker = null

        val elapsedMillis = now - marker.createdAtElapsedRealtime
        return marker.source.takeIf { elapsedMillis in 0..entryWindowMillis }
            ?: MapEntrySource.OTHER
    }

    private companion object {
        private const val DefaultEntryWindowMillis = 5_000L
    }
}
