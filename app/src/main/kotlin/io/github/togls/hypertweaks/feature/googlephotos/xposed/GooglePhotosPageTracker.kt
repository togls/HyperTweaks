package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

enum class GooglePhotosPage {
    Unknown,
    Home,
    Collections,
    Places,
    MapExplore,
    PlaceDetails,
}

class GooglePhotosPageTracker(
    private val logger: GooglePhotosProbeLogger,
) {
    private val currentPage = AtomicReference(GooglePhotosPage.Unknown)
    private val currentHost = AtomicReference(WeakReference<Activity>(null))

    fun onActivityObserved(
        activity: Activity,
        event: String,
    ) {
        val page = GooglePhotosPageResolver.fromActivity(activity.javaClass.name) ?: return
        updatePage(
            activity,
            page,
            "activity=" + activity.javaClass.name + ",event=" + event,
        )
    }

    fun onFragmentObserved(
        activity: Activity,
        fragmentClassName: String,
        event: String,
    ) {
        val page = GooglePhotosPageResolver.fromFragment(fragmentClassName) ?: return
        updatePage(
            activity,
            page,
            "fragment=" + fragmentClassName + ",event=" + event,
        )
    }

    fun onViewObserved(
        activity: Activity,
        summary: GooglePhotosViewSummary,
        trigger: String,
    ) {
        GooglePhotosPageResolver.fromViewSummary(activity.javaClass.name, summary)?.let { page ->
            updatePage(activity, page, "view_scan=" + trigger)
        }
    }

    fun onActivityDestroyed(activity: Activity) {
        if (currentHost.get().get() !== activity) {
            return
        }

        currentHost.set(WeakReference(null))
        val previousPage = currentPage.getAndSet(GooglePhotosPage.Unknown)
        if (previousPage != GooglePhotosPage.Unknown) {
            logger.pageChanged(previousPage, GooglePhotosPage.Unknown, activity, "activity_destroyed")
        }
    }

    fun currentPage(): GooglePhotosPage {
        return currentPage.get()
    }

    private fun updatePage(
        activity: Activity,
        page: GooglePhotosPage,
        reason: String,
    ) {
        currentHost.set(WeakReference(activity))
        val previousPage = currentPage.getAndSet(page)
        if (previousPage != page) {
            logger.pageChanged(previousPage, page, activity, reason)
        }
    }
}

internal object GooglePhotosPageResolver {
    fun fromActivity(className: String): GooglePhotosPage? {
        return when (className) {
            GooglePhotosClassNames.HomeActivity -> GooglePhotosPage.Home
            GooglePhotosClassNames.CollectionsActivity -> GooglePhotosPage.Collections
            GooglePhotosClassNames.MapExploreActivity -> GooglePhotosPage.MapExplore
            else -> null
        }
    }

    fun fromFragment(className: String): GooglePhotosPage? {
        return null
    }

    fun fromViewSummary(
        hostClassName: String,
        summary: GooglePhotosViewSummary,
    ): GooglePhotosPage? {
        if (summary.mapViewCount == 0 && summary.customMapViewClassNames.isEmpty()) {
            return null
        }

        return when (hostClassName) {
            GooglePhotosClassNames.MapExploreActivity -> GooglePhotosPage.MapExplore
            else -> null
        }
    }
}
