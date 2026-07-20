package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Activity
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.ChinaCoordinateConverter
import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

internal class GooglePhotosMapRenderHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val scopeTracker: GooglePhotosMapScopeTracker,
) {
    private val module = context.module
    private val conversionCounts = Collections.synchronizedMap(WeakHashMap<Activity, Int>())
    private lateinit var coordinateAccessors: CoordinateAccessors
    private lateinit var renderBinding: MapRenderBinding

    fun install(classLoader: ClassLoader) {
        val coordinateClass = classLoader.loadClass(LatLngClassName)
        coordinateAccessors = CoordinateAccessorResolver.resolve(coordinateClass)
            ?: error("LatLng accessors are ambiguous")
        val activityClass = classLoader.loadClass(GooglePhotosClassNames.MapExploreActivity)
        renderBinding = GooglePhotosMapRenderMethodMatcher(coordinateClass).find(activityClass)
            ?: error("Marker render method is ambiguous or unavailable")
        installRenderInterceptor()
        logger.renderHookInstalled()
    }

    fun onActivityDestroyed(activity: Activity) {
        synchronized(conversionCounts) {
            conversionCounts.remove(activity)
        }
    }

    private fun installRenderInterceptor() {
        renderBinding.method.isAccessible = true
        module.hook(renderBinding.method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                applyCoordinateConversion(chain.args.firstOrNull())
                chain.proceed()
            }
    }

    private fun applyCoordinateConversion(markerOptions: Any?) {
        val activity = scopeTracker.currentCollectionsMapActivity() ?: return
        if (markerOptions == null) {
            return
        }

        try {
            val originalPosition = renderBinding.positionField.get(markerOptions) ?: return
            val original = coordinateAccessors.read(originalPosition)
            val conversionResult = ChinaCoordinateConverter.wgs84ToGcj02Result(
                latitude = original.latitude,
                longitude = original.longitude,
            )
            if (conversionResult.failure != null) {
                logger.warning("convert_coordinate", conversionResult.failure)
                return
            }
            val converted = conversionResult.coordinate
            if (converted == original) {
                return
            }

            // MarkerOptions is an ephemeral render input, so changing it cannot alter Photos media data.
            renderBinding.positionField.set(markerOptions, coordinateAccessors.create(converted))
            recordConversion(activity)
        } catch (error: Exception) {
            logger.warning("convert_marker_position", error)
        }
    }

    private fun recordConversion(activity: Activity) {
        val convertedCount = synchronized(conversionCounts) {
            val nextCount = conversionCounts.getOrDefault(activity, 0) + 1
            conversionCounts[activity] = nextCount
            nextCount
        }
        if (convertedCount == 1) {
            logger.conversionApplied(convertedCount)
        }
    }

    private companion object {
        private const val LatLngClassName = "com.google.android.gms.maps.model.LatLng"
    }
}

internal data class MapRenderBinding(
    val method: Method,
    val positionField: Field,
)

internal class GooglePhotosMapRenderMethodMatcher(
    private val coordinateClass: Class<*>,
) {
    fun find(activityClass: Class<*>): MapRenderBinding? {
        // Photos and its bundled Maps facade are obfuscated together; structural matching survives renames.
        val controllerTypes = activityClass.declaredFields
            .asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map(Field::getType)
            .filter { it.classLoader === activityClass.classLoader }
            .distinct()
        val candidates = controllerTypes
            .flatMap { controllerType -> controllerType.declaredFields.asSequence() }
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map(Field::getType)
            .filter { it.classLoader === activityClass.classLoader }
            .distinct()
            .flatMap(::candidateBindings)
            .distinctBy(MapRenderBinding::method)
            .toList()
        return candidates.singleOrNull()
    }

    private fun candidateBindings(facadeType: Class<*>): Sequence<MapRenderBinding> {
        return facadeType.declaredMethods
            .asSequence()
            .filter { method -> method.parameterCount == 1 && method.returnType != Void.TYPE }
            .mapNotNull(::bindingFor)
    }

    private fun bindingFor(method: Method): MapRenderBinding? {
        val optionsType = method.parameterTypes.single()
        val positionFields = optionsType.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == coordinateClass
        }
        if (positionFields.size != 1 || !returnTypeExposesCoordinate(method.returnType)) {
            return null
        }

        return MapRenderBinding(
            method = method,
            positionField = positionFields.single().apply { isAccessible = true },
        )
    }

    private fun returnTypeExposesCoordinate(returnType: Class<*>): Boolean {
        return returnType.declaredMethods.any { method ->
            method.parameterCount == 0 && method.returnType == coordinateClass
        }
    }
}

internal data class CoordinateAccessors(
    val constructor: Constructor<*>,
    val latitudeField: Field,
    val longitudeField: Field,
) {
    fun read(value: Any): Coordinate {
        return Coordinate(
            latitude = latitudeField.getDouble(value),
            longitude = longitudeField.getDouble(value),
        )
    }

    fun create(coordinate: Coordinate): Any {
        return constructor.newInstance(coordinate.latitude, coordinate.longitude)
    }
}

internal object CoordinateAccessorResolver {
    fun resolve(coordinateClass: Class<*>): CoordinateAccessors? {
        val constructor = coordinateClass.getDeclaredConstructor(
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val coordinateFields = coordinateClass.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == Double::class.javaPrimitiveType
        }.onEach { field -> field.isAccessible = true }
        if (coordinateFields.size != 2) {
            return null
        }

        // Field names are obfuscated, so constructor probe values identify latitude and longitude safely.
        val probe = constructor.newInstance(LatitudeProbe, LongitudeProbe)
        val latitudeField = coordinateFields.singleOrNull { it.getDouble(probe) == LatitudeProbe }
        val longitudeField = coordinateFields.singleOrNull { it.getDouble(probe) == LongitudeProbe }
        if (latitudeField == null || longitudeField == null || latitudeField == longitudeField) {
            return null
        }
        return CoordinateAccessors(
            constructor = constructor,
            latitudeField = latitudeField.apply { isAccessible = true },
            longitudeField = longitudeField.apply { isAccessible = true },
        )
    }

    private const val LatitudeProbe = 12.3456789
    private const val LongitudeProbe = 98.7654321
}
