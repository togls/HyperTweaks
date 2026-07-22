package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import java.lang.reflect.Constructor

internal class GooglePhotosPreviewMarkerAnimationHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val module = context.module
    private val coordinatePolicy = PreviewMarkerAnimationCoordinatePolicy()
    private lateinit var coordinateClass: Class<*>
    private lateinit var markerClass: Class<*>
    private lateinit var coordinateAccessors: CoordinateAccessors

    fun install(classLoader: ClassLoader) {
        coordinateClass = classLoader.loadClass(LatLngClassName)
        markerClass = classLoader.loadClass(MarkerClassName)
        coordinateAccessors = CoordinateAccessorResolver.resolve(coordinateClass)
            ?: error("LatLng accessors are ambiguous")
        val listenerClass = classLoader.loadClass(AnimationListenerClassName)
        val constructors = PreviewMarkerAnimationBindingResolver().resolve(listenerClass)
        check(constructors.size == ExpectedConstructorCount) {
            "Preview marker animation constructors are ambiguous: ${constructors.size}"
        }
        installInterceptor(constructors.single())
    }

    private fun installInterceptor(constructor: Constructor<*>) {
        constructor.isAccessible = true
        module.hook(constructor)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain -> interceptConstructor(chain, constructor) }
    }

    private fun interceptConstructor(
        chain: XposedInterface.Chain,
        constructor: Constructor<*>,
    ): Any? {
        if (!PreviewMarkerAnimationArguments.matches(chain.args, coordinateClass, markerClass)) {
            return chain.proceed()
        }
        val target = readCoordinate(chain.args[TargetArgumentIndex]) ?: return chain.proceed()
        val session = sessionTracker.currentSession()?.toProbeLogSnapshot()
        val callCount = logger.previewMarkerAnimationInvoked(
            constructor.toGenericString(),
            target,
            session,
        )
        val result = coordinatePolicy.transform(target, sessionActive = session != null)
            ?: return chain.proceed()
        logger.previewMarkerAnimationResult(callCount, session, result)
        val converted = result.converted?.takeIf {
            result.outcome == LocationCoordinateOutcome.CONVERTED
        } ?: return chain.proceed()
        val convertedValue = createCoordinate(converted) ?: return chain.proceed()
        return chain.proceed(
            PreviewMarkerAnimationArguments.withTarget(chain.args, convertedValue),
        )
    }

    private fun readCoordinate(value: Any?): Coordinate? {
        if (value == null) return null
        return runCatching { coordinateAccessors.read(value) }
            .onFailure { error -> logger.warning("preview_marker_animation_read", error) }
            .getOrNull()
    }

    private fun createCoordinate(coordinate: Coordinate): Any? {
        return runCatching { coordinateAccessors.create(coordinate) }
            .onFailure { error -> logger.warning("preview_marker_animation_create", error) }
            .getOrNull()
    }

    private companion object {
        private const val AnimationListenerClassName = "apzz"
        private const val MarkerClassName = "bnej"
        private const val LatLngClassName = "com.google.android.gms.maps.model.LatLng"
        private const val ExpectedConstructorCount = 1
        private const val TargetArgumentIndex = 1
    }
}

internal class PreviewMarkerAnimationBindingResolver {
    fun resolve(listenerClass: Class<*>): List<Constructor<*>> {
        return listenerClass.declaredConstructors.filter { constructor ->
            constructor.parameterTypes.contentEquals(ExpectedParameterTypes)
        }
    }

    private companion object {
        val ExpectedParameterTypes = arrayOf(
            Any::class.java,
            Any::class.java,
            Any::class.java,
            Int::class.javaPrimitiveType,
        )
    }
}

internal object PreviewMarkerAnimationArguments {
    fun matches(
        arguments: List<Any?>,
        coordinateClass: Class<*>,
        markerClass: Class<*>,
    ): Boolean {
        return arguments.size == ArgumentCount &&
            coordinateClass.isInstance(arguments[StartArgumentIndex]) &&
            coordinateClass.isInstance(arguments[TargetArgumentIndex]) &&
            markerClass.isInstance(arguments[MarkerArgumentIndex]) &&
            arguments[VariantArgumentIndex] is Int
    }

    fun withTarget(arguments: List<Any?>, target: Any): Array<Any?> {
        return arguments.toTypedArray().apply {
            this[TargetArgumentIndex] = target
        }
    }

    private const val ArgumentCount = 4
    private const val StartArgumentIndex = 0
    private const val TargetArgumentIndex = 1
    private const val MarkerArgumentIndex = 2
    private const val VariantArgumentIndex = 3
}

internal class PreviewMarkerAnimationCoordinatePolicy(
    private val transformer: LocationCoordinateTransformer = LocationCoordinateTransformer(),
) {
    fun transform(target: Coordinate, sessionActive: Boolean): LocationCoordinateResult? {
        if (!sessionActive) return null
        return transformer.transform(target)
    }
}
