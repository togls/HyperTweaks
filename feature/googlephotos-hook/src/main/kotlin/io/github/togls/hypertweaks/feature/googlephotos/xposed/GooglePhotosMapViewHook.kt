package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method

internal class GooglePhotosMapViewHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val engine = context.engine

    fun install(classLoader: ClassLoader) {
        val mapViewClass = classLoader.loadClass(GooglePhotosClassNames.MapView)
        require(View::class.java.isAssignableFrom(mapViewClass)) {
            "Google Maps target does not extend android.view.View"
        }
        val attachedMethod = declaredMethod(mapViewClass, "onAttachedToWindow")
        val detachedMethod = declaredMethod(mapViewClass, "onDetachedFromWindow")
        val visibilityMethod = declaredMethod(
            mapViewClass,
            "onWindowVisibilityChanged",
            Int::class.javaPrimitiveType!!,
        )
        hookAfter(attachedMethod, "map_view_attached") { view, _ ->
            sessionTracker.onMapViewAttached(view, findHostActivity(view.context))
        }
        hookBefore(detachedMethod, "map_view_detached") { view, _ ->
            sessionTracker.onMapViewDetached(view, findHostActivity(view.context))
        }
        hookAfter(visibilityMethod, "map_view_visibility") { view, arguments ->
            val visibility = arguments.firstOrNull() as? Int ?: view.windowVisibility
            sessionTracker.onMapViewVisibilityChanged(view, findHostActivity(view.context), visibility)
        }
    }

    private fun hookBefore(
        method: Method,
        operation: String,
        callback: (View, List<Any?>) -> Unit,
    ) {
        hook(method) { chain ->
            invokeSafely(chain.thisObject, chain.args, operation, callback)
            chain.proceed()
        }
    }

    private fun hookAfter(
        method: Method,
        operation: String,
        callback: (View, List<Any?>) -> Unit,
    ) {
        hook(method) { chain ->
            val result = chain.proceed()
            invokeSafely(chain.thisObject, chain.args, operation, callback)
            result
        }
    }

    private fun hook(method: Method, interceptor: (HookChain) -> Any?) {
        method.isAccessible = true
        engine.hook(method, interceptor)
    }

    private fun invokeSafely(
        receiver: Any?,
        arguments: List<Any?>,
        operation: String,
        callback: (View, List<Any?>) -> Unit,
    ) {
        val view = receiver as? View ?: return
        try {
            callback(view, arguments)
        } catch (error: Exception) {
            logger.warning(operation, error)
        }
    }

    private fun findHostActivity(context: Context): Activity? {
        var currentContext: Context? = context
        repeat(MaximumContextDepth) {
            when (val candidate = currentContext) {
                is Activity -> return candidate
                is ContextWrapper -> {
                    val baseContext = candidate.baseContext
                    if (baseContext === candidate) return null
                    currentContext = baseContext
                }
                else -> return null
            }
        }
        return null
    }

    private fun declaredMethod(
        targetClass: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method = targetClass.getDeclaredMethod(methodName, *parameterTypes)

    private companion object {
        private const val MaximumContextDepth = 16
    }
}
