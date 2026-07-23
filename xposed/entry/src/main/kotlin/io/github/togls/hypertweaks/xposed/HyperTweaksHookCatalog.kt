package io.github.togls.hypertweaks.xposed

import io.github.togls.hypertweaks.core.xposed.HookFeatureCatalog
import io.github.togls.hypertweaks.feature.googlephotos.GooglePhotosHookFeatureProvider
import io.github.togls.hypertweaks.feature.ime.ImeHookFeatureProvider
import io.github.togls.hypertweaks.feature.keepalive.KeepAliveHookFeatureProvider

object HyperTweaksHookCatalog {
    fun create(): HookFeatureCatalog {
        return HookFeatureCatalog(
            providers = listOf(
                GooglePhotosHookFeatureProvider(),
                ImeHookFeatureProvider(),
                KeepAliveHookFeatureProvider(),
            ),
        )
    }
}
