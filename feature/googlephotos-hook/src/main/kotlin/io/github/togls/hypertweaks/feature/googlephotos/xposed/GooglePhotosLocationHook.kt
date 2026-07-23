package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal class GooglePhotosLocationHook(
    context: HookContext,
) {
    private val engine = context.engine
    private val logger = GooglePhotosLocationLogger(context.log)
    private val installed = AtomicBoolean(false)
    private val sessionTracker = GooglePhotosMapSessionTracker(logger)
    private val renderHook = GooglePhotosMapRenderHook(context, logger, sessionTracker)
    private val markerAnimationHook =
        GooglePhotosPreviewMarkerAnimationHook(context, logger, sessionTracker)
    private val initialPreviewSelectionHook =
        GooglePhotosInitialPreviewSelectionHook(context, logger, sessionTracker)
    private val mapLocationHook = GooglePhotosMapLocationHook(context, logger, sessionTracker)
    private val cameraUpdateHook = GooglePhotosCameraUpdateHook(context, logger, sessionTracker)
    private val s2QueryHook = GooglePhotosS2QueryHook(context, logger, sessionTracker)
    private val mapViewHook = GooglePhotosMapViewHook(context, logger, sessionTracker)

    fun install(classLoader: ClassLoader) {
        if (Application.getProcessName() != GooglePhotosClassNames.PackageName) {
            return
        }
        if (!installed.compareAndSet(false, true)) {
            return
        }

        logger.installBegin()
        val result = installHooks(classLoader)
        logger.installCompleted(result)
    }

    private fun installHooks(classLoader: ClassLoader): GooglePhotosHookInstallResult {
        val coordinator = GooglePhotosHookInstallCoordinator(
            onBegin = logger::installTargetBegin,
            onSuccess = logger::installTargetSuccess,
            onFailure = logger::installTargetFailure,
        )
        return coordinator.install(
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.LIFECYCLE) {
                installActivityLifecycleHooks(classLoader.loadClass(ActivityClassName))
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MAP_VIEW) {
                mapViewHook.install(classLoader)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MARKER_API) {
                renderHook.install(classLoader)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MARKER_ANIMATION) {
                markerAnimationHook.install(classLoader)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.INITIAL_PREVIEW_SELECTION) {
                initialPreviewSelectionHook.install(classLoader)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MAP_LOCATION) {
                mapLocationHook.install(classLoader)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.CAMERA_UPDATE) {
                cameraUpdateHook.install(classLoader)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.S2_QUERY) {
                s2QueryHook.install(classLoader)
            },
        )
    }

    private fun installActivityLifecycleHooks(activityClass: Class<*>) {
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
        interceptor: (HookChain) -> Any?,
    ) {
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

    private companion object {
        private const val ActivityClassName = "android.app.Activity"
    }
}

internal enum class GooglePhotosInstallTarget(
    val logName: String,
    val isStrategy: Boolean,
) {
    LIFECYCLE("lifecycle", false),
    MAP_VIEW("map_view", false),
    MARKER_API("marker_api", true),
    MARKER_ANIMATION("marker_animation", true),
    INITIAL_PREVIEW_SELECTION("initial_preview_selection", true),
    MAP_LOCATION("map_location", true),
    CAMERA_UPDATE("camera_update", true),
    S2_QUERY("s2_query", true),
}

internal data class GooglePhotosHookInstallStep(
    val target: GooglePhotosInstallTarget,
    val install: () -> Unit,
)

internal data class GooglePhotosHookInstallResult(
    private val outcomes: Map<GooglePhotosInstallTarget, Boolean>,
) {
    fun installed(target: GooglePhotosInstallTarget): Boolean = outcomes[target] == true
}

internal class GooglePhotosHookInstallCoordinator(
    private val onBegin: (GooglePhotosInstallTarget) -> Unit = {},
    private val onSuccess: (GooglePhotosInstallTarget) -> Unit = {},
    private val onFailure: (GooglePhotosInstallTarget, Throwable) -> Unit = { _, _ -> },
) {
    fun install(vararg steps: GooglePhotosHookInstallStep): GooglePhotosHookInstallResult {
        val outcomes = linkedMapOf<GooglePhotosInstallTarget, Boolean>()
        steps.forEach { step -> outcomes[step.target] = installStep(step) }
        return GooglePhotosHookInstallResult(outcomes)
    }

    private fun installStep(step: GooglePhotosHookInstallStep): Boolean {
        onBegin(step.target)
        return try {
            step.install()
            onSuccess(step.target)
            true
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            onFailure(step.target, error)
            false
        }
    }
}
