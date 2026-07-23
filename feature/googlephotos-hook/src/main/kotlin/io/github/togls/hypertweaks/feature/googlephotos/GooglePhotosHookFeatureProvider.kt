package io.github.togls.hypertweaks.feature.googlephotos

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookFeatureProvider
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookTarget
import io.github.togls.hypertweaks.feature.googlephotos.data.GooglePhotosPackageMatcher
import io.github.togls.hypertweaks.feature.googlephotos.xposed.GooglePhotosLocationHook

class GooglePhotosHookFeatureProvider : HookFeatureProvider {
    override fun features(): List<HookFeature> = listOf(GooglePhotosLocationFeature)
}

private object GooglePhotosLocationFeature : HookFeature {
    override val id: String = "googlephotos.location"
    override val preferenceKey: String = RemotePreferenceKeys.GooglePhotosLocationEnabled
    override val targets: Set<HookTarget> = setOf(
        HookTarget.Packages(setOf(GooglePhotosPackageMatcher.GooglePhotosPackage)),
    )

    override fun supports(environment: HookEnvironment): Boolean {
        return super.supports(environment) &&
            environment.processName == GooglePhotosPackageMatcher.GooglePhotosPackage
    }

    override fun install(context: HookFeatureContext): HookInstallResult {
        GooglePhotosLocationHook(context.child("GooglePhotosLocationHook"))
            .install(context.environment.classLoader)
        return HookInstallResult.Installed(
            installedTargets = setOf("google_photos_location"),
        )
    }
}
