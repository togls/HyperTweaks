package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal class GooglePhotosPreviewMarkerHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val module = context.module
    private val transformer = MarkerCoordinateTransformer()
    private val targets = PreviewMarkerTargetRegistry()
    private val hookedMethods = mutableSetOf<Method>()
    private lateinit var coordinateAccessors: CoordinateAccessors
    private lateinit var binding: PreviewMarkerBinding

    fun install(classLoader: ClassLoader) {
        val coordinateClass = classLoader.loadClass(LatLngClassName)
        coordinateAccessors = CoordinateAccessorResolver.resolve(coordinateClass)
            ?: error("LatLng accessors are ambiguous")
        val activityClass = classLoader.loadClass(GooglePhotosClassNames.MapExploreActivity)
        val renderBinding = GooglePhotosMapRenderMethodMatcher(coordinateClass)
            .inspect(activityClass)
            .binding ?: error("Marker render method is ambiguous or unavailable")
        val imageClass = classLoader.loadClass(BitmapClassName)
        val report = PreviewMarkerBindingResolver(imageClass, coordinateClass).inspect(renderBinding)
        logger.previewMatcherCompleted(report)
        binding = report.binding ?: error("Preview marker callback is ambiguous or unavailable")
    }

    fun onActivityAvailable(activity: Activity) {
        if (!::binding.isInitialized || activity.javaClass != binding.renderBinding.controllerField.declaringClass) {
            return
        }
        val controller = binding.renderBinding.controllerField.get(activity) ?: return
        val callback = binding.callbackField.get(controller) ?: return
        val callbackMethod = resolveConcreteCallbackMethod(callback.javaClass)
            ?: error("Preview marker callback implementation is ambiguous")
        targets.register(callback, controller, binding.selectedMarkerField)
        installCallbackInterceptor(callbackMethod)
        logger.previewBound(callback.javaClass.name, callbackMethod.toGenericString())
    }

    private fun resolveConcreteCallbackMethod(callbackClass: Class<*>): Method? {
        return callbackClass.declaredMethods.singleOrNull { method ->
            method.parameterTypes.contentEquals(binding.callbackMethod.parameterTypes) &&
                method.returnType == Void.TYPE
        }?.apply { isAccessible = true }
    }

    @Synchronized
    private fun installCallbackInterceptor(method: Method) {
        if (!hookedMethods.add(method)) return
        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val convertedPosition = convertSelectedPosition(
                    chain.thisObject,
                    chain.args.getOrNull(CoordinateArgumentIndex),
                ) ?: return@intercept chain.proceed()
                val updatedArguments = chain.args.toTypedArray()
                updatedArguments[CoordinateArgumentIndex] = convertedPosition
                chain.proceed(updatedArguments)
            }
    }

    private fun convertSelectedPosition(callback: Any?, coordinateValue: Any?): Any? {
        if (callback == null || coordinateValue == null || !targets.hasSelectedMarker(callback)) return null
        val original = try {
            coordinateAccessors.read(coordinateValue)
        } catch (error: Exception) {
            logger.warning("preview_read_coordinate", error)
            return null
        }
        var convertedValue: Any? = null
        val result = transformer.transform(callback, original) { converted ->
            convertedValue = coordinateAccessors.create(converted)
        }
        logger.previewResult(sessionTracker.currentSession()?.toProbeLogSnapshot(), result)
        return convertedValue
    }

    private companion object {
        private const val LatLngClassName = "com.google.android.gms.maps.model.LatLng"
        private const val BitmapClassName = "android.graphics.Bitmap"
        private const val CoordinateArgumentIndex = 1
    }
}

internal data class PreviewMarkerBinding(
    val renderBinding: MapRenderBinding,
    val selectedMarkerField: Field,
    val callbackField: Field,
    val callbackMethod: Method,
)

internal data class PreviewMarkerMatchReport(
    val selectedMarkerFieldCount: Int,
    val callbackCount: Int,
    val binding: PreviewMarkerBinding?,
)

internal class PreviewMarkerBindingResolver(
    private val imageClass: Class<*>,
    private val coordinateClass: Class<*>,
) {
    fun inspect(renderBinding: MapRenderBinding): PreviewMarkerMatchReport {
        val controllerClass = renderBinding.controllerField.type
        val markerFields = controllerClass.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == renderBinding.method.returnType
        }
        val callbacks = controllerClass.declaredFields.mapNotNull { field -> callbackCandidate(field) }
        val binding = if (markerFields.size == 1 && callbacks.size == 1) {
            val (callbackField, callbackMethod) = callbacks.single()
            PreviewMarkerBinding(
                renderBinding = renderBinding,
                selectedMarkerField = markerFields.single().apply { isAccessible = true },
                callbackField = callbackField.apply { isAccessible = true },
                callbackMethod = callbackMethod.apply { isAccessible = true },
            )
        } else {
            null
        }
        return PreviewMarkerMatchReport(markerFields.size, callbacks.size, binding)
    }

    private fun callbackCandidate(field: Field): Pair<Field, Method>? {
        if (Modifier.isStatic(field.modifiers)) return null
        val methods = field.type.declaredMethods.filter { method ->
            method.returnType == Void.TYPE && method.parameterCount == 2 &&
                method.parameterTypes[0] == imageClass && method.parameterTypes[1] == coordinateClass
        }
        return methods.singleOrNull()?.let { field to it }
    }
}

internal class PreviewMarkerTargetRegistry {
    private val targets = WeakHashMap<Any, PreviewMarkerTarget>()

    @Synchronized
    fun register(callback: Any, controller: Any, selectedMarkerField: Field) {
        targets[callback] = PreviewMarkerTarget(WeakReference(controller), selectedMarkerField)
    }

    @Synchronized
    fun hasSelectedMarker(callback: Any): Boolean {
        val target = targets[callback] ?: return false
        val controller = target.controller.get() ?: return false
        return target.selectedMarkerField.get(controller) != null
    }
}

internal data class PreviewMarkerTarget(
    val controller: WeakReference<Any>,
    val selectedMarkerField: Field,
)
