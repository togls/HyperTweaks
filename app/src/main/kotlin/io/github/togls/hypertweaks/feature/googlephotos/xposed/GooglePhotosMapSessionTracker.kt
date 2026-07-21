package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.view.View

internal enum class MapSessionRejectionReason {
    NO_HOST_ACTIVITY,
    HOST_NOT_RESUMED,
    MAP_VIEW_NOT_ATTACHED,
    SINGLE_PHOTO_MAP,
    UNKNOWN_HOST,
    ACTIVITY_PAUSED,
    VIEW_DETACHED,
    ACTIVITY_DESTROYED,
}

internal data class GooglePhotosMapSession<ActivityKey : Any>(
    val sessionId: Long,
    val hostActivity: ActivityKey,
    val hostClassName: String,
)

internal data class MapSessionTransition<ActivityKey : Any>(
    val activeSession: GooglePhotosMapSession<ActivityKey>? = null,
    val activatedSession: GooglePhotosMapSession<ActivityKey>? = null,
    val deactivatedSession: GooglePhotosMapSession<ActivityKey>? = null,
    val reason: MapSessionRejectionReason? = null,
    val attachedViewCount: Int = 0,
    val currentResumedActivity: ActivityKey? = null,
)

internal class GooglePhotosMapSessionStateMachine<ActivityKey : Any, ViewKey : Any> {
    private val activityClassNames = mutableMapOf<ActivityKey, String>()
    private val resumedActivities = mutableSetOf<ActivityKey>()
    private val attachedViewHosts = mutableMapOf<ViewKey, ActivityKey?>()
    private var activeSession: GooglePhotosMapSession<ActivityKey>? = null
    private var currentResumedActivity: ActivityKey? = null
    private var nextSessionId = 1L

    @Synchronized
    fun onActivityCreated(activity: ActivityKey, className: String) {
        activityClassNames[activity] = className
    }

    @Synchronized
    fun onActivityResumed(activity: ActivityKey, className: String): MapSessionTransition<ActivityKey> {
        activityClassNames[activity] = className
        resumedActivities += activity
        currentResumedActivity = activity
        val previousSession = activeSession?.takeIf { it.hostActivity !== activity }
        if (previousSession != null) activeSession = null
        return evaluate(activity).copy(deactivatedSession = previousSession)
    }

    @Synchronized
    fun onActivityPaused(activity: ActivityKey): MapSessionTransition<ActivityKey> {
        resumedActivities -= activity
        if (currentResumedActivity === activity) currentResumedActivity = null
        return deactivateIfCurrent(activity, MapSessionRejectionReason.ACTIVITY_PAUSED)
    }

    @Synchronized
    fun onActivityDestroyed(activity: ActivityKey): MapSessionTransition<ActivityKey> {
        val transition = deactivateIfCurrent(activity, MapSessionRejectionReason.ACTIVITY_DESTROYED)
        resumedActivities -= activity
        activityClassNames.remove(activity)
        attachedViewHosts.entries.removeAll { it.value === activity }
        if (currentResumedActivity === activity) currentResumedActivity = null
        return transition.copy(currentResumedActivity = currentResumedActivity)
    }

    @Synchronized
    fun onMapViewAttached(view: ViewKey, hostActivity: ActivityKey?): MapSessionTransition<ActivityKey> {
        attachedViewHosts[view] = hostActivity
        return hostActivity?.let(::evaluate)
            ?: transition(reason = MapSessionRejectionReason.NO_HOST_ACTIVITY)
    }

    @Synchronized
    fun onMapViewDetached(view: ViewKey): MapSessionTransition<ActivityKey> {
        val hostActivity = attachedViewHosts.remove(view)
            ?: return transition(reason = MapSessionRejectionReason.VIEW_DETACHED)
        val session = activeSession
        if (session?.hostActivity === hostActivity && attachedViewCount(hostActivity) == 0) {
            return deactivateIfCurrent(hostActivity, MapSessionRejectionReason.VIEW_DETACHED)
        }
        return transition(activeSession = session)
    }

    @Synchronized
    fun currentSession(): GooglePhotosMapSession<ActivityKey>? = activeSession

    @Synchronized
    fun currentResumedActivity(): ActivityKey? = currentResumedActivity

    private fun evaluate(activity: ActivityKey): MapSessionTransition<ActivityKey> {
        val reason = rejectionReason(activity)
        if (reason != null) return transition(reason = reason)
        val current = activeSession
        if (current?.hostActivity === activity) return transition(activeSession = current)

        val activated = GooglePhotosMapSession(
            sessionId = nextSessionId++,
            hostActivity = activity,
            hostClassName = activityClassNames.getValue(activity),
        )
        activeSession = activated
        return transition(
            activeSession = activated,
            activatedSession = activated,
            deactivatedSession = current,
        )
    }

    private fun rejectionReason(activity: ActivityKey): MapSessionRejectionReason? {
        val hostClassName = activityClassNames[activity]
            ?: return MapSessionRejectionReason.UNKNOWN_HOST
        if (hostClassName == GooglePhotosClassNames.MapExploreActivity) {
            return MapSessionRejectionReason.SINGLE_PHOTO_MAP
        }
        if (hostClassName !in AllowedHostClassNames) return MapSessionRejectionReason.UNKNOWN_HOST
        if (activity !in resumedActivities) return MapSessionRejectionReason.HOST_NOT_RESUMED
        if (attachedViewCount(activity) == 0) return MapSessionRejectionReason.MAP_VIEW_NOT_ATTACHED
        return null
    }

    private fun deactivateIfCurrent(
        activity: ActivityKey,
        reason: MapSessionRejectionReason,
    ): MapSessionTransition<ActivityKey> {
        val current = activeSession
        if (current?.hostActivity !== activity) return transition(reason = reason)
        activeSession = null
        return transition(deactivatedSession = current, reason = reason)
    }

    private fun attachedViewCount(activity: ActivityKey): Int {
        return attachedViewHosts.values.count { it === activity }
    }

    private fun transition(
        activeSession: GooglePhotosMapSession<ActivityKey>? = this.activeSession,
        activatedSession: GooglePhotosMapSession<ActivityKey>? = null,
        deactivatedSession: GooglePhotosMapSession<ActivityKey>? = null,
        reason: MapSessionRejectionReason? = null,
    ): MapSessionTransition<ActivityKey> {
        return MapSessionTransition(
            activeSession = activeSession,
            activatedSession = activatedSession,
            deactivatedSession = deactivatedSession,
            reason = reason,
            attachedViewCount = activeSession?.let { attachedViewCount(it.hostActivity) } ?: 0,
            currentResumedActivity = currentResumedActivity,
        )
    }

    private companion object {
        val AllowedHostClassNames = setOf(
            GooglePhotosClassNames.HomeActivity,
            GooglePhotosClassNames.CollectionsActivity,
        )
    }
}

internal class GooglePhotosMapSessionTracker(
    private val logger: GooglePhotosLocationLogger,
) {
    private val stateMachine = GooglePhotosMapSessionStateMachine<Activity, View>()

    fun onActivityCreated(activity: Activity) {
        stateMachine.onActivityCreated(activity, activity.javaClass.name)
        logger.activityEvent("create", activitySnapshot(activity))
    }

    fun onActivityResumed(activity: Activity) {
        val transition = stateMachine.onActivityResumed(activity, activity.javaClass.name)
        logger.activityEvent("resume", activitySnapshot(activity))
        logger.sessionTransition("ACTIVITY_RESUMED", transition.toLogSnapshot())
    }

    fun onActivityPaused(activity: Activity) {
        val transition = stateMachine.onActivityPaused(activity)
        logger.activityEvent("pause", activitySnapshot(activity))
        logger.sessionTransition("ACTIVITY_PAUSED", transition.toLogSnapshot())
    }

    fun onActivityDestroyed(activity: Activity) {
        val transition = stateMachine.onActivityDestroyed(activity)
        logger.activityEvent("destroy", activitySnapshot(activity))
        logger.sessionTransition("ACTIVITY_DESTROYED", transition.toLogSnapshot())
    }

    fun onMapViewAttached(view: View, hostActivity: Activity?) {
        logger.mapViewEvent("attached", viewSnapshot(view, hostActivity))
        val transition = stateMachine.onMapViewAttached(view, hostActivity)
        logger.sessionTransition("VIEW_ATTACHED", transition.toLogSnapshot())
    }

    fun onMapViewDetached(view: View, hostActivity: Activity?) {
        logger.mapViewEvent("detached", viewSnapshot(view, hostActivity))
        val transition = stateMachine.onMapViewDetached(view)
        logger.sessionTransition("VIEW_DETACHED", transition.toLogSnapshot())
    }

    fun onMapViewVisibilityChanged(view: View, hostActivity: Activity?, visibility: Int) {
        logger.mapViewEvent("visibility changed", viewSnapshot(view, hostActivity, visibility))
    }

    fun currentSession(): GooglePhotosMapSession<Activity>? = stateMachine.currentSession()

    private fun activitySnapshot(activity: Activity): ActivityLogSnapshot {
        val resumedActivity = stateMachine.currentResumedActivity()
        return ActivityLogSnapshot(
            activityClass = activity.javaClass.name,
            activityIdentity = identity(activity),
            isChangingConfigurations = activity.isChangingConfigurations,
            isFinishing = activity.isFinishing,
            isDestroyed = activity.isDestroyed,
            currentResumedActivity = resumedActivity?.javaClass?.name,
        )
    }

    private fun viewSnapshot(
        view: View,
        hostActivity: Activity?,
        visibility: Int = view.windowVisibility,
    ): MapViewLogSnapshot {
        return MapViewLogSnapshot(
            viewClass = view.javaClass.name,
            viewIdentity = identity(view),
            hostActivity = hostActivity?.javaClass?.name,
            hostIdentity = hostActivity?.let(::identity),
            parentClass = view.parent?.javaClass?.name,
            rootViewClass = view.rootView?.javaClass?.name,
            windowVisibility = visibility,
            width = view.width,
            height = view.height,
            isShown = view.isShown,
            thread = Thread.currentThread().name,
            parentPath = parentPath(view),
        )
    }

    private fun MapSessionTransition<Activity>.toLogSnapshot(): MapSessionLogSnapshot {
        val session = activeSession ?: activatedSession ?: deactivatedSession
        return MapSessionLogSnapshot(
            sessionId = session?.sessionId,
            hostActivity = session?.hostClassName,
            hostIdentity = session?.hostActivity?.let(::identity),
            attachedViewCount = attachedViewCount,
            currentResumedActivity = currentResumedActivity?.javaClass?.name,
            activated = activatedSession != null,
            deactivated = deactivatedSession != null,
            reason = reason,
        )
    }

    private fun parentPath(view: View): String {
        val parents = mutableListOf<String>()
        var currentParent = view.parent
        while (currentParent != null && parents.size < MaximumParentDepth) {
            parents += currentParent.javaClass.name
            currentParent = currentParent.parent
        }
        return parents.joinToString(" > ")
    }

    private fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))

    private companion object {
        private const val MaximumParentDepth = 8
    }
}
