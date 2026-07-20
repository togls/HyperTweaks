package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal class GooglePhotosLocationHook(
    context: HookContext,
) {
    private val module = context.module
    private val logger = GooglePhotosLocationLogger(context.log)
    private val installed = AtomicBoolean(false)
    private val scopeTracker = GooglePhotosMapScopeTracker(logger)
    private val renderHook = GooglePhotosMapRenderHook(context, logger, scopeTracker)
    private val heatmapIndexHook = GooglePhotosHeatmapIndexHook(context, logger, scopeTracker)

    fun install(classLoader: ClassLoader) {
        if (Application.getProcessName() != GooglePhotosClassNames.PackageName) {
            return
        }
        if (!installed.compareAndSet(false, true)) {
            return
        }

        renderHook.install(classLoader)
        heatmapIndexHook.install(classLoader)
        val activityClass = classLoader.loadClass(ActivityClassName)
        installActivityLifecycleHooks(activityClass)
        logger.hookInstalled()
    }

    private fun installActivityLifecycleHooks(activityClass: Class<*>) {
        hookAfter(activityClass, "onCreate", arrayOf(Bundle::class.java), "activity_create") {
            scopeTracker.onActivityCreated(it)
        }
        hookAfter(activityClass, "onResume", emptyArray(), "activity_resume") {
            scopeTracker.onActivityResumed(it)
        }
        hookBefore(activityClass, "onPause", emptyArray(), "activity_pause") {
            scopeTracker.onActivityPaused(it)
        }
        hookBefore(activityClass, "onDestroy", emptyArray(), "activity_destroy") {
            scopeTracker.onActivityDestroyed(it)
            renderHook.onActivityDestroyed(it)
        }
    }

    private fun hookBefore(
        activityClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        operation: String,
        callback: (Activity) -> Unit,
    ) {
        val method = activityMethod(activityClass, methodName, parameterTypes)
        hook(method) { chain ->
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
        val method = activityMethod(activityClass, methodName, parameterTypes)
        hook(method) { chain ->
            val result = chain.proceed()
            invokeSafely(chain.thisObject, operation, callback)
            result
        }
    }

    private fun hook(
        method: Method,
        interceptor: (XposedInterface.Chain) -> Any?,
    ) {
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(interceptor)
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

    private companion object {
        private const val ActivityClassName = "android.app.Activity"
    }
}
