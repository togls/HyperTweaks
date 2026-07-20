package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import java.util.Collections
import java.util.WeakHashMap

class GooglePhotosActivityProbe(
    private val logger: GooglePhotosProbeLogger,
    private val fragmentProbe: GooglePhotosFragmentProbe,
    private val viewProbe: GooglePhotosViewProbe,
    private val pageTracker: GooglePhotosPageTracker,
    private val mapScopeTracker: GooglePhotosMapScopeTracker,
    private val coordinateProbe: GooglePhotosCoordinateProbeHook,
) {
    private val resumedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    fun onCreated(activity: Activity) {
        logger.activityEvent("create", activity)
        mapScopeTracker.onActivityCreated(activity)
        fragmentProbe.register(activity)
        pageTracker.onActivityObserved(activity, "create")
    }

    fun onResumed(activity: Activity) {
        if (resumedActivities.add(activity)) {
            logger.activityEvent("resume", activity)
        }

        pageTracker.onActivityObserved(activity, "resume")
        mapScopeTracker.onActivityResumed(activity)
        viewProbe.schedule(activity, "activity_resume")
    }

    fun onPaused(activity: Activity) {
        logger.activityEvent("pause", activity)
        mapScopeTracker.onActivityPaused(activity)
    }

    fun onDestroyed(activity: Activity) {
        logger.activityEvent("destroy", activity)
        resumedActivities.remove(activity)
        mapScopeTracker.onActivityDestroyed(activity)
        coordinateProbe.onActivityDestroyed(activity)
        fragmentProbe.unregister(activity)
        viewProbe.onActivityDestroyed(activity)
        pageTracker.onActivityDestroyed(activity)
    }

    fun onNewIntent(activity: Activity) {
        logger.activityEvent("new_intent", activity)
        pageTracker.onActivityObserved(activity, "new_intent")
    }

    fun onWindowFocused(activity: Activity) {
        logger.activityEvent("window_focus", activity)
        pageTracker.onActivityObserved(activity, "window_focus")
        viewProbe.schedule(activity, "window_focus")
    }
}
