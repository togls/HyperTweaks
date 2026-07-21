package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.view.View

internal enum class MapSessionRejectionReason {
    NOT_MAP_EXPLORE,
    ACTIVITY_PAUSED,
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

    // 暂时保留该字段，兼容现有 Logger。
    // MapView 已经不再参与 Session 判定，因此固定为 0。
    val attachedViewCount: Int = 0,

    val currentResumedActivity: ActivityKey? = null,
)

internal class GooglePhotosMapSessionStateMachine<ActivityKey : Any> {
    private val activityClassNames = mutableMapOf<ActivityKey, String>()

    private var activeSession: GooglePhotosMapSession<ActivityKey>? = null
    private var currentResumedActivity: ActivityKey? = null
    private var nextSessionId = 1L

    /**
     * 必须在 onCreate 阶段激活。
     *
     * Google Photos 的 S2Index 构建发生在 MapExploreActivity.onCreate
     * 和 onResume 之间，如果等待 onResume，会错过主要坐标批次。
     */
    @Synchronized
    fun onActivityCreated(
        activity: ActivityKey,
        className: String,
    ): MapSessionTransition<ActivityKey> {
        activityClassNames[activity] = className

        return if (className == GooglePhotosClassNames.MapExploreActivity) {
            activate(activity)
        } else {
            transition(reason = MapSessionRejectionReason.NOT_MAP_EXPLORE)
        }
    }

    /**
     * onResume 仅作为兜底确认。
     *
     * 正常情况下，MapExploreActivity 已经在 onCreate 时建立 Session，
     * 此处不会重复生成 sessionId。
     */
    @Synchronized
    fun onActivityResumed(
        activity: ActivityKey,
        className: String,
    ): MapSessionTransition<ActivityKey> {
        activityClassNames[activity] = className
        currentResumedActivity = activity

        if (className == GooglePhotosClassNames.MapExploreActivity) {
            return activate(activity)
        }

        /*
         * 防御性清理：
         * 如果因为生命周期异常，旧 MapExploreActivity 的 Session
         * 没有在 onPause 中清理，当其他 Activity 恢复时强制结束。
         */
        val current = activeSession
        if (current != null && current.hostActivity !== activity) {
            activeSession = null

            return transition(
                deactivatedSession = current,
                reason = MapSessionRejectionReason.NOT_MAP_EXPLORE,
            )
        }

        return transition(reason = MapSessionRejectionReason.NOT_MAP_EXPLORE)
    }

    @Synchronized
    fun onActivityPaused(
        activity: ActivityKey,
    ): MapSessionTransition<ActivityKey> {
        if (currentResumedActivity === activity) {
            currentResumedActivity = null
        }

        return deactivateIfCurrent(
            activity,
            MapSessionRejectionReason.ACTIVITY_PAUSED,
        )
    }

    @Synchronized
    fun onActivityDestroyed(
        activity: ActivityKey,
    ): MapSessionTransition<ActivityKey> {
        val transition = deactivateIfCurrent(
            activity,
            MapSessionRejectionReason.ACTIVITY_DESTROYED,
        )

        activityClassNames.remove(activity)

        if (currentResumedActivity === activity) {
            currentResumedActivity = null
        }

        return transition.copy(
            currentResumedActivity = currentResumedActivity,
        )
    }

    @Synchronized
    fun currentSession(): GooglePhotosMapSession<ActivityKey>? {
        return activeSession
    }

    @Synchronized
    fun currentResumedActivity(): ActivityKey? {
        return currentResumedActivity
    }

    private fun activate(
        activity: ActivityKey,
    ): MapSessionTransition<ActivityKey> {
        val current = activeSession

        // onCreate 已激活时，onResume 不重复创建 Session。
        if (current?.hostActivity === activity) {
            return transition(activeSession = current)
        }

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

    private fun deactivateIfCurrent(
        activity: ActivityKey,
        reason: MapSessionRejectionReason,
    ): MapSessionTransition<ActivityKey> {
        val current = activeSession

        if (current?.hostActivity !== activity) {
            return transition(reason = reason)
        }

        activeSession = null

        return transition(
            deactivatedSession = current,
            reason = reason,
        )
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
            attachedViewCount = 0,
            currentResumedActivity = currentResumedActivity,
        )
    }
}

internal class GooglePhotosMapSessionTracker(
    private val logger: GooglePhotosLocationLogger,
) {
    private val stateMachine =
        GooglePhotosMapSessionStateMachine<Activity>()

    fun onActivityCreated(activity: Activity) {
        val transition = stateMachine.onActivityCreated(
            activity,
            activity.javaClass.name,
        )

        logger.activityEvent(
            "create",
            activitySnapshot(activity),
        )

        /*
         * 必须记录 ACTIVITY_CREATED。
         * 这条日志用于确认 Session 在 S2Index 构建前已经激活。
         */
        logger.sessionTransition(
            "ACTIVITY_CREATED",
            transition.toLogSnapshot(),
        )
    }

    fun onActivityResumed(activity: Activity) {
        val transition = stateMachine.onActivityResumed(
            activity,
            activity.javaClass.name,
        )

        logger.activityEvent(
            "resume",
            activitySnapshot(activity),
        )

        logger.sessionTransition(
            "ACTIVITY_RESUMED",
            transition.toLogSnapshot(),
        )
    }

    fun onActivityPaused(activity: Activity) {
        val transition = stateMachine.onActivityPaused(activity)

        logger.activityEvent(
            "pause",
            activitySnapshot(activity),
        )

        logger.sessionTransition(
            "ACTIVITY_PAUSED",
            transition.toLogSnapshot(),
        )
    }

    fun onActivityDestroyed(activity: Activity) {
        val transition = stateMachine.onActivityDestroyed(activity)

        logger.activityEvent(
            "destroy",
            activitySnapshot(activity),
        )

        logger.sessionTransition(
            "ACTIVITY_DESTROYED",
            transition.toLogSnapshot(),
        )
    }

    /**
     * MapView Hook 仅用于 Probe。
     *
     * 当前固定类 com.google.maps.api.android.lib6.impl.au
     * 在设备上无法通过 Google Photos 主 ClassLoader 加载，
     * 因此它不能再参与 Session 激活或结束。
     */
    fun onMapViewAttached(
        view: View,
        hostActivity: Activity?,
    ) {
        logger.mapViewEvent(
            "attached",
            viewSnapshot(view, hostActivity),
        )
    }

    fun onMapViewDetached(
        view: View,
        hostActivity: Activity?,
    ) {
        logger.mapViewEvent(
            "detached",
            viewSnapshot(view, hostActivity),
        )
    }

    fun onMapViewVisibilityChanged(
        view: View,
        hostActivity: Activity?,
        visibility: Int,
    ) {
        logger.mapViewEvent(
            "visibility changed",
            viewSnapshot(
                view,
                hostActivity,
                visibility,
            ),
        )
    }

    fun currentSession(): GooglePhotosMapSession<Activity>? {
        return stateMachine.currentSession()
    }

    private fun activitySnapshot(
        activity: Activity,
    ): ActivityLogSnapshot {
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

    private fun MapSessionTransition<Activity>.toLogSnapshot():
        MapSessionLogSnapshot {
        val session =
            activeSession ?: activatedSession ?: deactivatedSession

        return MapSessionLogSnapshot(
            sessionId = session?.sessionId,
            hostActivity = session?.hostClassName,
            hostIdentity = session?.hostActivity?.let(::identity),
            attachedViewCount = attachedViewCount,
            currentResumedActivity =
                currentResumedActivity?.javaClass?.name,
            activated = activatedSession != null,
            deactivated = deactivatedSession != null,
            reason = reason,
        )
    }

    private fun parentPath(view: View): String {
        val parents = mutableListOf<String>()
        var currentParent = view.parent

        while (
            currentParent != null &&
            parents.size < MaximumParentDepth
        ) {
            parents += currentParent.javaClass.name
            currentParent = currentParent.parent
        }

        return parents.joinToString(" > ")
    }

    private fun identity(value: Any): String {
        return Integer.toHexString(
            System.identityHashCode(value),
        )
    }

    private companion object {
        private const val MaximumParentDepth = 8
    }
}