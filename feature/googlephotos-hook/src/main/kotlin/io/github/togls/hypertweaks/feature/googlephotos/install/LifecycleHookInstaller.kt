package io.github.togls.hypertweaks.feature.googlephotos.install

import android.app.Activity
import android.os.Bundle
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.session.GooglePhotosMapSessionTracker
import io.github.togls.hypertweaks.feature.googlephotos.xposed.GooglePhotosLocationLogger
import io.github.togls.hypertweaks.feature.googlephotos.xposed.GooglePhotosMapLocationHook
import java.lang.reflect.Method

internal class LifecycleHookInstaller(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
    private val mapLocationHook: GooglePhotosMapLocationHook,
) {
    private val engine = context.engine

    fun install(activityClass: Class<*>) {
        hookAfter(activityClass, "onCreate", arrayOf(Bundle::class.java), "activity_create") {
            sessionTracker.onActivityCreated(it)
            mapLocationHook.onActivityAvailable(it)
        }
        hookAfter(activityClass, "onResume", emptyArray(), "activity_resume") {
            sessionTracker.onActivityResumed(it)
            mapLocationHook.onActivityAvailable(it)
        }
        hookBefore(activityClass, "onPause", emptyArray(), "activity_pause") {
            sessionTracker.onActivityPaused(it)
        }
        hookBefore(activityClass, "onDestroy", emptyArray(), "activity_destroy") {
            sessionTracker.onActivityDestroyed(it)
        }
    }

    private fun hookBefore(
        activityClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        operation: String,
        callback: (Activity) -> Unit,
    ) {
        hook(activityMethod(activityClass, methodName, parameterTypes)) { chain ->
            invokeSafely(chain.thisObject, operation, callback)
            chain.proceed()
        }
    }

    private fun hookAfter(
        activityClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        operation: String,
        callback: (Activity) -> Unit,
    ) {
        hook(activityMethod(activityClass, methodName, parameterTypes)) { chain ->
            val result = chain.proceed()
            invokeSafely(chain.thisObject, operation, callback)
            result
        }
    }

    private fun hook(method: Method, interceptor: (HookChain) -> Any?) {
        engine.hook(method, interceptor)
    }

    private fun activityMethod(
        activityClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
    ): Method {
        return activityClass.getDeclaredMethod(methodName, *parameterTypes).apply {
            isAccessible = true
        }
    }

    private fun invokeSafely(
        receiver: Any?,
        operation: String,
        callback: (Activity) -> Unit,
    ) {
        val activity = receiver as? Activity ?: return
        try {
            callback(activity)
        } catch (error: Exception) {
            logger.warning(operation, error)
        }
    }
}
