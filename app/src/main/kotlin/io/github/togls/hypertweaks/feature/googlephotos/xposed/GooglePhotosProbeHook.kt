package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.app.Application
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.data.GooglePhotosPackageMatcher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class GooglePhotosProbeHook(
    context: HookContext,
) {
    private val module = context.module
    private val log = context.log
    private val installed = AtomicBoolean(false)
    private val resumedActivityClassNames = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            log.i("GooglePhotosProbe: hook already installed")
            return
        }

        try {
            val onResumeMethod = classLoader
                .loadClass(ACTIVITY_CLASS_NAME)
                .getDeclaredMethod(ON_RESUME_METHOD_NAME)
                .apply { isAccessible = true }

            module.hook(onResumeMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    logActivityResume(chain.thisObject)
                    result
                }

            log.i(
                message = "GooglePhotosProbe: hook installed",
                "package" to GooglePhotosPackageMatcher.GooglePhotosPackage,
                "process" to Application.getProcessName(),
            )
        } catch (error: Throwable) {
            installed.set(false)
            log.e("GooglePhotosProbe: failed to install hook", error)
            throw error
        }
    }

    private fun logActivityResume(instance: Any?) {
        val activity = instance as? Activity ?: return
        val activityClassName = activity.javaClass.name

        if (!resumedActivityClassNames.add(activityClassName)) {
            return
        }

        log.i(
            message = "GooglePhotosProbe: activity resumed",
            "package" to GooglePhotosPackageMatcher.GooglePhotosPackage,
            "process" to Application.getProcessName(),
            "activity" to activityClassName,
        )
    }

    private companion object {
        private const val ACTIVITY_CLASS_NAME = "android.app.Activity"
        private const val ON_RESUME_METHOD_NAME = "onResume"
    }
}
