package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookFeatureProvider
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookTarget
import io.github.togls.hypertweaks.feature.ime.xposed.DeadZoneHook
import io.github.togls.hypertweaks.feature.ime.xposed.ImePackageMatcher
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodBottomManagerHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodManagerServiceHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodManagerServiceImplHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodServiceHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarControllerHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarInflaterHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarViewHook

class ImeHookFeatureProvider : HookFeatureProvider {
    override fun features(): List<HookFeature> {
        return listOf(ImeSystemServerFeature, ImePackageFeature)
    }
}

private object ImeSystemServerFeature : HookFeature {
    override val id: String = "ime.system-server"
    override val preferenceKey: String = RemotePreferenceKeys.ImeEnabled
    override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

    override fun install(context: HookFeatureContext): HookInstallResult {
        val hookName = if (context.environment.sdkInt >= Android16Api) {
            InputMethodManagerServiceImplHook(context.child("InputMethodManagerServiceImpl"))
                .install(context.environment.classLoader)
            "input_method_manager_service_impl"
        } else {
            InputMethodManagerServiceHook(context.child("InputMethodManagerService"))
                .install(context.environment.classLoader)
            "input_method_manager_service"
        }
        return HookInstallResult.Installed(installedTargets = setOf(hookName))
    }

    private const val Android16Api = 36
}

private object ImePackageFeature : HookFeature {
    override val id: String = "ime.package"
    override val preferenceKey: String = RemotePreferenceKeys.ImeEnabled
    override val targets: Set<HookTarget> = setOf(
        HookTarget.Packages(ImePackageMatcher.packageNames),
    )

    override fun install(context: HookFeatureContext): HookInstallResult {
        val classLoader = context.environment.classLoader
        val installers = linkedMapOf<String, () -> Unit>(
            "input_method_service" to {
                InputMethodServiceHook(context.child("InputMethodService")).install(classLoader)
            },
            "navigation_bar_controller" to {
                NavigationBarControllerHook(context.child("NavigationBarController"))
                    .install(classLoader)
            },
            "navigation_bar_inflater" to {
                NavigationBarInflaterHook(context.child("NavigationBarInflater")).install(classLoader)
            },
            "navigation_bar_view" to {
                NavigationBarViewHook(context.child("NavigationBarView")).install(classLoader)
            },
            "dead_zone" to {
                DeadZoneHook(context.child("DeadZone")).install(classLoader)
            },
            "input_method_bottom_manager" to {
                InputMethodBottomManagerHook(context.child("InputMethodBottomManager"))
                    .install(classLoader)
            },
        )
        return installIndependently(context, installers)
    }

    private fun installIndependently(
        context: HookFeatureContext,
        installers: Map<String, () -> Unit>,
    ): HookInstallResult.Installed {
        val installedTargets = mutableSetOf<String>()
        val failedTargets = mutableSetOf<String>()
        installers.forEach { (target, install) ->
            try {
                install()
                installedTargets += target
            } catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                failedTargets += target
                context.log.error(
                    event = "hook.install.failed",
                    throwable = error,
                    fields = mapOf("subtarget" to target),
                )
            }
        }
        return HookInstallResult.Installed(installedTargets, failedTargets)
    }
}
