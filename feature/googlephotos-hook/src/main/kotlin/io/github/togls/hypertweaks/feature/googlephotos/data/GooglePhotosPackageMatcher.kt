package io.github.togls.hypertweaks.feature.googlephotos.data

object GooglePhotosPackageMatcher {
    const val GooglePhotosPackage: String = "com.google.android.apps.photos"

    fun matches(packageName: String): Boolean {
        return packageName == GooglePhotosPackage
    }
}
