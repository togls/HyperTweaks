package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

class GooglePhotosFragmentProbe(
    private val logger: GooglePhotosProbeLogger,
    private val viewProbe: GooglePhotosViewProbe,
    private val pageTracker: GooglePhotosPageTracker,
) {
    private val callbacksByActivity = Collections.synchronizedMap(
        WeakHashMap<FragmentActivity, FragmentManager.FragmentLifecycleCallbacks>(),
    )

    fun register(activity: Activity) {
        val fragmentActivity = activity as? FragmentActivity ?: return
        val callbacks = callbacksFor(fragmentActivity) ?: return

        runCatching {
            fragmentActivity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                callbacks,
                true,
            )
        }.onFailure { error ->
            callbacksByActivity.remove(fragmentActivity)
            logger.warning("GooglePhotosProbe: failed to register fragment probe", error)
        }
    }

    fun unregister(activity: Activity) {
        val fragmentActivity = activity as? FragmentActivity ?: return
        val callbacks = callbacksByActivity.remove(fragmentActivity) ?: return

        runCatching {
            fragmentActivity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(callbacks)
        }.onFailure { error ->
            logger.warning("GooglePhotosProbe: failed to unregister fragment probe", error)
        }
    }

    fun onVisibilityChanged(
        instance: Any?,
        event: String,
        propertyName: String,
        value: Boolean,
    ) {
        val fragment = instance as? Fragment ?: return
        val host = fragment.activity ?: return
        val className = fragment.javaClass.name

        logger.fragmentVisibility(host, className, event, propertyName, value)
        pageTracker.onFragmentObserved(host, className, event)
    }

    private fun callbacksFor(
        activity: FragmentActivity,
    ): FragmentManager.FragmentLifecycleCallbacks? {
        synchronized(callbacksByActivity) {
            if (callbacksByActivity.containsKey(activity)) {
                return null
            }

            return createCallbacks(WeakReference(activity)).also { callbacks ->
                callbacksByActivity[activity] = callbacks
            }
        }
    }

    private fun createCallbacks(
        hostReference: WeakReference<Activity>,
    ): FragmentManager.FragmentLifecycleCallbacks {
        return object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreAttached(
                manager: FragmentManager,
                fragment: Fragment,
                context: android.content.Context,
            ) {
                report(hostReference, fragment, "pre_attached", false)
            }

            override fun onFragmentCreated(
                manager: FragmentManager,
                fragment: Fragment,
                savedInstanceState: android.os.Bundle?,
            ) {
                report(hostReference, fragment, "created", false)
            }

            override fun onFragmentViewCreated(
                manager: FragmentManager,
                fragment: Fragment,
                view: View,
                savedInstanceState: android.os.Bundle?,
            ) {
                report(hostReference, fragment, "view_created", true)
            }

            override fun onFragmentResumed(
                manager: FragmentManager,
                fragment: Fragment,
            ) {
                report(hostReference, fragment, "resumed", false)
            }

            override fun onFragmentPaused(
                manager: FragmentManager,
                fragment: Fragment,
            ) {
                report(hostReference, fragment, "paused", false)
            }

            override fun onFragmentDestroyed(
                manager: FragmentManager,
                fragment: Fragment,
            ) {
                report(hostReference, fragment, "destroyed", false)
            }
        }
    }

    private fun report(
        hostReference: WeakReference<Activity>,
        fragment: Fragment,
        event: String,
        scanViewTree: Boolean,
    ) {
        val host = hostReference.get() ?: fragment.activity ?: return
        val className = fragment.javaClass.name

        logger.fragmentEvent(event, host, className)
        pageTracker.onFragmentObserved(host, className, event)
        if (scanViewTree) {
            viewProbe.schedule(host, "fragment_view_created")
        }
    }
}
