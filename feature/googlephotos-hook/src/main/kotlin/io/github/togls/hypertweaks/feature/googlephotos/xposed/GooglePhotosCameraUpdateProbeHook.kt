package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class GooglePhotosCameraUpdateHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val engine = context.engine
    private val coordinatePolicy = CameraUpdateCoordinatePolicy()
    private lateinit var coordinateAccessors: CoordinateAccessors

    fun install(classLoader: ClassLoader) {
        val coordinateClass = classLoader.loadClass(LatLngClassName)
        coordinateAccessors = CoordinateAccessorResolver.resolve(coordinateClass)
            ?: error("LatLng accessors are ambiguous")
        val factoryClass = classLoader.loadClass(CameraUpdateFactoryClassName)
        val methods = CameraUpdateBindingResolver(coordinateClass).resolve(factoryClass)
        check(methods.size == ExpectedFactoryMethodCount) {
            "Camera update methods are ambiguous: ${methods.size}"
        }
        methods.forEach(::installProbe)
    }

    private fun installProbe(method: Method) {
        method.isAccessible = true
        engine.hook(method) { chain -> interceptCameraUpdate(chain, method) }
    }

    private fun interceptCameraUpdate(chain: HookChain, method: Method): Any? {
        val coordinateValue = chain.args.firstOrNull()
        val original = readCoordinate(coordinateValue)
        val session = sessionTracker.currentSession()?.toProbeLogSnapshot()
        val callCount = logger.cameraUpdateInvoked(
            method = method.toGenericString(),
            coordinate = original,
            session = session,
            stack = cameraUpdateStack(),
        )
        val result = original?.let { coordinatePolicy.transform(it, session != null) }
            ?: return chain.proceed()
        logger.cameraUpdateResult(callCount, session, result)
        val converted = result.converted?.takeIf { result.outcome == LocationCoordinateOutcome.CONVERTED }
            ?: return chain.proceed()
        val convertedValue = createCoordinate(converted) ?: return chain.proceed()
        val updatedArguments = chain.args.toTypedArray()
        updatedArguments[CoordinateArgumentIndex] = convertedValue
        return chain.proceed(updatedArguments)
    }

    private fun readCoordinate(value: Any?): Coordinate? {
        if (value == null) return null
        return runCatching { coordinateAccessors.read(value) }
            .onFailure { error -> logger.warning("camera_update_read_coordinate", error) }
            .getOrNull()
    }

    private fun createCoordinate(coordinate: Coordinate): Any? {
        return runCatching { coordinateAccessors.create(coordinate) }
            .onFailure { error -> logger.warning("camera_update_create_coordinate", error) }
            .getOrNull()
    }

    private fun cameraUpdateStack(): String {
        return Thread.currentThread().stackTrace.asSequence()
            .filterNot { frame -> frame.className.startsWith(ModulePackagePrefix) }
            .filterNot { frame -> frame.className == VmStackClassName }
            .take(StackFrameLimit)
            .joinToString(" <- ") { frame -> "${frame.className}.${frame.methodName}:${frame.lineNumber}" }
    }

    private companion object {
        private const val CameraUpdateFactoryClassName = "bmeb"
        private const val LatLngClassName = "com.google.android.gms.maps.model.LatLng"
        private const val CoordinateArgumentIndex = 0
        private const val ExpectedFactoryMethodCount = 2
        private const val StackFrameLimit = 16
        private const val ModulePackagePrefix = "io.github.togls.hypertweaks"
        private const val VmStackClassName = "dalvik.system.VMStack"
    }
}

internal class CameraUpdateBindingResolver(
    private val coordinateClass: Class<*>,
) {
    fun resolve(factoryClass: Class<*>): List<Method> {
        return factoryClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                (
                    method.parameterTypes.contentEquals(arrayOf(coordinateClass)) ||
                        method.parameterTypes.contentEquals(
                            arrayOf(coordinateClass, Float::class.javaPrimitiveType),
                        )
                    )
        }
    }
}

internal class CameraUpdateCoordinatePolicy(
    private val transformer: LocationCoordinateTransformer = LocationCoordinateTransformer(),
) {
    fun transform(original: Coordinate, sessionActive: Boolean): LocationCoordinateResult? {
        if (!sessionActive) return null
        return transformer.transform(original)
    }
}
