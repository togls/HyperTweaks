package io.github.togls.hypertweaks.feature.googlephotos.resolver

import android.app.Activity
import android.view.View
import java.lang.reflect.Modifier

internal enum class GooglePhotosClassLoaderRole {
    APPLICATION,
    MAPS_INTERNAL,
    PLATFORM,
}

internal enum class GooglePhotosTarget(
    val logName: String,
    val exactClassName: String,
    val classLoaderRole: GooglePhotosClassLoaderRole,
    val validator: (Class<*>) -> Boolean = { true },
) {
    ACTIVITY(
        "activity",
        "android.app.Activity",
        GooglePhotosClassLoaderRole.PLATFORM,
        Activity::class.java::isAssignableFrom,
    ),
    HOME_ACTIVITY(
        "home_activity",
        "com.google.android.apps.photos.home.HomeActivity",
        GooglePhotosClassLoaderRole.APPLICATION,
        Activity::class.java::isAssignableFrom,
    ),
    COLLECTIONS_ACTIVITY(
        "collections_activity",
        "com.google.android.apps.photos.collectionstab.collectionsgridpage.CollectionsGridPageActivity",
        GooglePhotosClassLoaderRole.APPLICATION,
        Activity::class.java::isAssignableFrom,
    ),
    MAP_EXPLORE_ACTIVITY(
        "map_explore_activity",
        "com.google.android.apps.photos.mapexplore.ui.MapExploreActivity",
        GooglePhotosClassLoaderRole.APPLICATION,
        Activity::class.java::isAssignableFrom,
    ),
    MAP_VIEW(
        "map_view",
        "com.google.maps.api.android.lib6.impl.au",
        GooglePhotosClassLoaderRole.MAPS_INTERNAL,
        View::class.java::isAssignableFrom,
    ),
    LAT_LNG(
        "lat_lng",
        "com.google.android.gms.maps.model.LatLng",
        GooglePhotosClassLoaderRole.APPLICATION,
        ::hasCoordinateStructure,
    ),
    LOCATION(
        "location",
        "android.location.Location",
        GooglePhotosClassLoaderRole.PLATFORM,
    ),
    CAMERA_UPDATE_FACTORY(
        "camera_update_factory",
        "bmeb",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    ANIMATION_LISTENER(
        "animation_listener",
        "apzz",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    MARKER(
        "marker",
        "bnej",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    PREVIEW_SELECTION(
        "preview_selection",
        "atxa",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    MEDIA(
        "media",
        "bsdv",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    PREVIEW_CONTROLLER(
        "preview_controller",
        "ahdq",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    S2_INDEX(
        "s2_index",
        "com.google.android.apps.photos.geo.S2Index",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    S2_RESULT(
        "s2_result",
        "com.google.android.apps.photos.geo.S2Index\$ResultImpl",
        GooglePhotosClassLoaderRole.APPLICATION,
    ),
    S2_BUILDER(
        "s2_builder",
        "com.google.android.apps.photos.geo.S2Index\$BuilderImpl",
        GooglePhotosClassLoaderRole.APPLICATION,
        ::hasSynchronizedHeatmapMethod,
    ),
}

internal data class GooglePhotosKnownTargetProfile(
    val versionName: String,
    val classNames: Map<GooglePhotosTarget, String>,
)

internal object GooglePhotosKnownTargetProfiles {
    val Photos783 = GooglePhotosKnownTargetProfile(
        versionName = "7.83",
        classNames = mapOf(
            GooglePhotosTarget.CAMERA_UPDATE_FACTORY to "bmeb",
            GooglePhotosTarget.ANIMATION_LISTENER to "apzz",
            GooglePhotosTarget.MARKER to "bnej",
            GooglePhotosTarget.PREVIEW_SELECTION to "atxa",
            GooglePhotosTarget.MEDIA to "bsdv",
            GooglePhotosTarget.PREVIEW_CONTROLLER to "ahdq",
        ),
    )
}

internal object GooglePhotosClassNames {
    const val PackageName = "com.google.android.apps.photos"
    val HomeActivity = GooglePhotosTarget.HOME_ACTIVITY.exactClassName
    val CollectionsActivity = GooglePhotosTarget.COLLECTIONS_ACTIVITY.exactClassName
    val MapExploreActivity = GooglePhotosTarget.MAP_EXPLORE_ACTIVITY.exactClassName
    val MapView = GooglePhotosTarget.MAP_VIEW.exactClassName
}

private fun hasCoordinateStructure(targetClass: Class<*>): Boolean {
    val hasConstructor = targetClass.declaredConstructors.any { constructor ->
        constructor.parameterTypes.contentEquals(
            arrayOf(Double::class.javaPrimitiveType, Double::class.javaPrimitiveType),
        )
    }
    val coordinateFieldCount = targetClass.declaredFields.count { field ->
        !Modifier.isStatic(field.modifiers) && field.type == Double::class.javaPrimitiveType
    }
    return hasConstructor && coordinateFieldCount == 2
}

private fun hasSynchronizedHeatmapMethod(targetClass: Class<*>): Boolean {
    val expectedParameters = arrayOf(
        LongArray::class.java,
        FloatArray::class.java,
        FloatArray::class.java,
        LongArray::class.java,
        Int::class.javaPrimitiveType,
    )
    return targetClass.declaredMethods.any { method ->
        !Modifier.isStatic(method.modifiers) &&
            Modifier.isSynchronized(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(expectedParameters)
    }
}
