package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookFeatureProvider
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookTarget
import io.github.togls.hypertweaks.feature.ime.xposed.ImePackageMatcher

class ImeHookFeatureProvider : HookFeatureProvider {
    override fun features(): List<HookFeature> {
        return listOf(ImeSystemServerFeature(), ImePackageFeature())
    }
}

internal class ImeSystemServerFeature(
    private val strategies: List<SystemServerStrategy> = listOf(
        Android34To35Strategy(),
        Android36PlusStrategy(),
    ),
) : HookFeature {
    override val id: String = "ime.system-server"
    override val preferenceKey: String = RemotePreferenceKeys.ImeEnabled
    override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

    override fun install(context: HookFeatureContext): HookInstallResult {
        val sdkInt = context.environment.sdkInt
        val strategy = strategies.firstOrNull { candidate -> candidate.supports(sdkInt) }
            ?: return HookInstallResult.Unsupported(
                reason = "Unsupported Android API $sdkInt; requires API 34+",
            )
        context.log.info(
            event = "ime.system_server.strategy.selected",
            fields = mapOf("sdk_int" to sdkInt.toString(), "strategy" to strategy.id),
        )
        return strategy.install(context.child("system_server"))
    }
}

internal class ImePackageFeature(
    private val strategy: InputMethodPackageStrategy = InputMethodPackageStrategy(),
) : HookFeature {
    override val id: String = "ime.package"
    override val preferenceKey: String = RemotePreferenceKeys.ImeEnabled
    override val targets: Set<HookTarget> = setOf(
        HookTarget.Packages(ImePackageMatcher.packageNames),
    )

    override fun install(context: HookFeatureContext): HookInstallResult {
        return strategy.install(context.child("package"))
    }
}
