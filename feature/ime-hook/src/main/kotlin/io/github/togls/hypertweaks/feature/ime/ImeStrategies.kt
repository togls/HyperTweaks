package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeInstallCoordinator
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstaller
import io.github.togls.hypertweaks.feature.ime.policy.ImeVersionPolicy
import io.github.togls.hypertweaks.feature.ime.xposed.DeadZoneHook
import io.github.togls.hypertweaks.feature.ime.xposed.ImePackageMatcher
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodBottomManagerHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodManagerServiceHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodManagerServiceImplHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodServiceHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarControllerHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarInflaterHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarViewHook

internal interface SystemServerStrategy {
    val id: String

    fun supports(sdkInt: Int): Boolean

    fun install(context: HookFeatureContext): HookInstallResult.Installed
}

internal class Android34To35Strategy(
    private val coordinator: ImeInstallCoordinator = ImeInstallCoordinator(),
    private val installers: List<ImeTargetInstaller> = android34To35Installers(),
) : SystemServerStrategy {
    override val id: String = "android_34_35"

    override fun supports(sdkInt: Int): Boolean {
        return sdkInt in ImeVersionPolicy.MinimumSupportedApi until ImeVersionPolicy.Android16Api
    }

    override fun install(context: HookFeatureContext): HookInstallResult.Installed {
        return coordinator.install(context.child(id), installers)
    }
}

internal class Android36PlusStrategy(
    private val coordinator: ImeInstallCoordinator = ImeInstallCoordinator(),
    private val installers: List<ImeTargetInstaller> = android36PlusInstallers(),
) : SystemServerStrategy {
    override val id: String = "android_36_plus"

    override fun supports(sdkInt: Int): Boolean {
        return sdkInt >= ImeVersionPolicy.Android16Api
    }

    override fun install(context: HookFeatureContext): HookInstallResult.Installed {
        return coordinator.install(context.child(id), installers)
    }
}

internal class InputMethodPackageStrategy(
    private val matcher: (String) -> Boolean = ImePackageMatcher::matches,
    private val coordinator: ImeInstallCoordinator = ImeInstallCoordinator(),
    private val installers: List<ImeTargetInstaller> = inputMethodPackageInstallers(),
) {
    fun install(context: HookFeatureContext): HookInstallResult {
        val environment = context.environment
        val matched = matcher(environment.packageName)
        context.log.info(
            event = "ime.package.match",
            fields = mapOf(
                "package_name" to environment.packageName,
                "matched" to matched.toString(),
            ),
        )
        if (!matched) {
            return HookInstallResult.Unsupported(
                reason = "Unsupported input method package: ${environment.packageName}",
            )
        }
        if (!ImeVersionPolicy.supportsInputMethodPackage(environment.sdkInt)) {
            return HookInstallResult.Unsupported(
                reason = "Unsupported Android API ${environment.sdkInt}; requires API 34+",
            )
        }
        return coordinator.install(context.child("input_method_package"), installers)
    }
}

private fun android34To35Installers(): List<ImeTargetInstaller> {
    return listOf(
        ImeTargetInstaller("input_method_manager_service") { context ->
            InputMethodManagerServiceHook(context.child("InputMethodManagerService"))
                .install(context.environment.classLoader)
        },
    )
}

private fun android36PlusInstallers(): List<ImeTargetInstaller> {
    return listOf(
        ImeTargetInstaller("input_method_manager_service_impl") { context ->
            InputMethodManagerServiceImplHook(context.child("InputMethodManagerServiceImpl"))
                .install(context.environment.classLoader)
        },
    )
}

private fun inputMethodPackageInstallers(): List<ImeTargetInstaller> {
    return listOf(
        packageInstaller("input_method_service") { context ->
            InputMethodServiceHook(context.child("InputMethodService"))
                .install(context.environment.classLoader)
        },
        packageInstaller("navigation_bar_controller") { context ->
            NavigationBarControllerHook(context.child("NavigationBarController"))
                .install(context.environment.classLoader)
        },
        packageInstaller("navigation_bar_inflater") { context ->
            NavigationBarInflaterHook(context.child("NavigationBarInflater"))
                .install(context.environment.classLoader)
        },
        packageInstaller("navigation_bar_view") { context ->
            NavigationBarViewHook(context.child("NavigationBarView"))
                .install(context.environment.classLoader)
        },
        packageInstaller("dead_zone") { context ->
            DeadZoneHook(context.child("DeadZone")).install(context.environment.classLoader)
        },
        packageInstaller("input_method_bottom_manager") { context ->
            InputMethodBottomManagerHook(context.child("InputMethodBottomManager"))
                .install(context.environment.classLoader)
        },
    )
}

private fun packageInstaller(
    target: String,
    install: (HookFeatureContext) -> List<ImeTargetInstallResult>,
): ImeTargetInstaller {
    return ImeTargetInstaller(target, install)
}
