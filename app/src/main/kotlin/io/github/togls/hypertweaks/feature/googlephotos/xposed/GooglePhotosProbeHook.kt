package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class GooglePhotosProbeHook(
    context: HookContext,
) {
    private val module = context.module
    private val log = context.log
    private val installed = AtomicBoolean(false)
    private val logger = GooglePhotosProbeLogger(log)
    private val pageTracker = GooglePhotosPageTracker(logger)
    private val mapScopeTracker = GooglePhotosMapScopeTracker(logger)
    private val coordinateProbe = GooglePhotosCoordinateProbeHook(
        context,
        logger,
        mapScopeTracker,
    )
    private val viewProbe = GooglePhotosViewProbe(logger, pageTracker)
    private val fragmentProbe = GooglePhotosFragmentProbe(logger, viewProbe, pageTracker)
    private val activityProbe = GooglePhotosActivityProbe(
        logger,
        fragmentProbe,
        viewProbe,
        pageTracker,
        mapScopeTracker,
        coordinateProbe,
    )

    fun install(classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            log.i("GooglePhotosProbe: hook already installed")
            return
        }

        runCatching {
            val activityClass = classLoader.loadClass(ActivityClassName)
            installActivityLifecycleHooks(activityClass)
            installFragmentVisibilityHooks(classLoader)
            coordinateProbe.install(classLoader)
        }.onSuccess {
            log.i(
                message = "GooglePhotosProbe: hook installed",
                "package" to GooglePhotosClassNames.PackageName,
                "process" to Application.getProcessName(),
            )
        }.onFailure { error ->
            installed.set(false)
            log.e("GooglePhotosProbe: failed to install hook", error)
            throw error
        }
    }

    private fun installActivityLifecycleHooks(activityClass: Class<*>) {
        hookActivityMethod(activityClass, "onCreate", arrayOf(Bundle::class.java)) { activity, _ ->
            activityProbe.onCreated(activity)
        }
        hookActivityMethod(activityClass, "onResume", emptyArray()) { activity, _ ->
            activityProbe.onResumed(activity)
        }
        hookActivityMethod(activityClass, "onPause", emptyArray()) { activity, _ ->
            activityProbe.onPaused(activity)
        }
        hookActivityMethod(activityClass, "onDestroy", emptyArray()) { activity, _ ->
            activityProbe.onDestroyed(activity)
        }
        hookActivityMethod(activityClass, "onNewIntent", arrayOf(Intent::class.java)) { activity, _ ->
            activityProbe.onNewIntent(activity)
        }
        hookActivityMethod(
            activityClass,
            "onWindowFocusChanged",
            arrayOf(Boolean::class.javaPrimitiveType!!),
        ) { activity, arguments ->
            if (arguments.firstOrNull() == true) {
                activityProbe.onWindowFocused(activity)
            }
        }
    }

    private fun installFragmentVisibilityHooks(classLoader: ClassLoader) {
        val fragmentClass = runCatching {
            classLoader.loadClass(GooglePhotosClassNames.Fragment)
        }.onFailure {
            logger.warning("GooglePhotosProbe: Fragment class is unavailable")
        }.getOrNull() ?: return

        FragmentVisibilityHooks.forEach { spec ->
            runCatching {
                val method = fragmentClass.getDeclaredMethod(
                    spec.methodName,
                    Boolean::class.javaPrimitiveType,
                ).apply {
                    isAccessible = true
                }
                hookAfter(method) { chain ->
                    val value = chain.args.firstOrNull() as? Boolean
                    if (value != null) {
                        fragmentProbe.onVisibilityChanged(
                            chain.thisObject,
                            spec.event,
                            spec.propertyName,
                            value,
                        )
                    }
                }
            }.onFailure { error ->
                logger.warning(
                    "GooglePhotosProbe: failed to hook Fragment." + spec.methodName,
                    error,
                )
            }
        }
    }

    private fun hookActivityMethod(
        activityClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        callback: (Activity, List<Any?>) -> Unit,
    ) {
        val method = activityClass.getDeclaredMethod(methodName, *parameterTypes).apply {
            isAccessible = true
        }
        hookAfter(method) { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null) {
                callback(activity, chain.args)
            }
        }
    }

    private fun hookAfter(
        method: Method,
        callback: (XposedInterface.Chain) -> Unit,
    ) {
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                callback(chain)
                result
            }
    }

    private data class FragmentVisibilityHook(
        val methodName: String,
        val event: String,
        val propertyName: String,
    )

    private companion object {
        private const val ActivityClassName = "android.app.Activity"

        private val FragmentVisibilityHooks = listOf(
            FragmentVisibilityHook("onHiddenChanged", "hidden_changed", "hidden"),
            FragmentVisibilityHook("setMenuVisibility", "menu_visibility", "menuVisible"),
            FragmentVisibilityHook("setUserVisibleHint", "user_visible_hint", "userVisible"),
        )
    }
}
