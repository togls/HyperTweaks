package io.github.togls.hypertweaks.core.xposed

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.util.HookLog
import io.github.togls.hypertweaks.feature.ime.xposed.DeadZoneHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodBottomManagerHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodManagerServiceHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodManagerServiceImplHook
import io.github.togls.hypertweaks.feature.ime.xposed.InputMethodServiceHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarControllerHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarInflaterHook
import io.github.togls.hypertweaks.feature.ime.xposed.NavigationBarViewHook
import io.github.togls.hypertweaks.feature.keepalive.xposed.KeepAliveHook
import io.github.togls.hypertweaks.feature.keepalive.xposed.OomAdjProtectHook

class HookRegistry(
    private val module: XposedModule,
) {
    private val installLogger = HookInstallLogger(module)

    private val systemServerHooks: List<SystemServerHookSpec> = listOf(
        KeepAliveSystemServerHookSpec,
        OomAdjProtectSystemServerHookSpec,
        ImeSystemServerHookSpec,
    )

    private val packageHooks: List<PackageHookSpec> = listOf(
        InputMethodServicePackageHookSpec,
        NavigationBarControllerPackageHookSpec,
        NavigationBarInflaterPackageHookSpec,
        NavigationBarViewPackageHookSpec,
        DeadZonePackageHookSpec,
        InputMethodBottomManagerPackageHookSpec,
    )

    fun installSystemServerHooks(classLoader: ClassLoader) {
        installLogger.startSystemServer()

        systemServerHooks.forEach { spec ->
            val target = "system_server"

            if (!isFeatureEnabled(spec.feature)) {
                installLogger.skippedDisabled(
                    name = spec.name,
                    target = target,
                    feature = spec.feature,
                )
                return@forEach
            }

            runCatching {
                spec.install(
                    module = module,
                    classLoader = classLoader,
                )
            }.onSuccess {
                installLogger.installed(
                    name = spec.name,
                    target = target,
                    feature = spec.feature,
                )
            }.onFailure { error ->
                installLogger.failed(
                    name = spec.name,
                    target = target,
                    feature = spec.feature,
                    error = error,
                )
            }
        }
    }

    fun installPackageHooks(param: PackageReadyParam) {
        if (!param.isFirstPackage) {
            return
        }

        val packageName = param.packageName

        installLogger.startPackage(packageName)

        packageHooks.forEach { spec ->
            if (!isFeatureEnabled(spec.feature)) {
                installLogger.skippedDisabled(
                    name = spec.name,
                    target = packageName,
                    feature = spec.feature,
                )
                return@forEach
            }

            if (!spec.isSupported(param)) {
                return@forEach
            }

            runCatching {
                spec.install(
                    module = module,
                    param = param,
                )
            }.onSuccess {
                installLogger.installed(
                    name = spec.name,
                    target = packageName,
                    feature = spec.feature,
                )
            }.onFailure { error ->
                installLogger.failed(
                    name = spec.name,
                    target = packageName,
                    feature = spec.feature,
                    error = error,
                )
            }
        }
    }

    private fun isFeatureEnabled(feature: HookFeature): Boolean {
        return runCatching {
            module.getRemotePreferences(RemotePreferenceKeys.GroupName)
                .getBoolean(feature.preferenceKey, false)
        }.onFailure { error ->
            HookLog.w(
                module,
                "failed to read feature toggle: feature=$feature key=${feature.preferenceKey}",
                error,
            )
        }.getOrDefault(false)
    }

    private object KeepAliveSystemServerHookSpec : SystemServerHookSpec {
        override val name: String = "KeepAliveHook"
        override val feature: HookFeature = HookFeature.KeepAlive

        override fun install(
            module: XposedModule,
            classLoader: ClassLoader,
        ) {
            KeepAliveHook(module).installSystemServer(classLoader)
        }
    }

    private object OomAdjProtectSystemServerHookSpec : SystemServerHookSpec {
        override val name: String = "OomAdjProtectHook"
        override val feature: HookFeature = HookFeature.KeepAlive

        override fun install(
            module: XposedModule,
            classLoader: ClassLoader,
        ) {
            OomAdjProtectHook(module).installSystemServer(classLoader)
        }
    }

    private object ImeSystemServerHookSpec : SystemServerHookSpec {
        override val name: String = "ImeSystemServerHook"
        override val feature: HookFeature = HookFeature.Ime

        override fun install(
            module: XposedModule,
            classLoader: ClassLoader,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                InputMethodManagerServiceImplHook(module).install(classLoader)
            } else {
                InputMethodManagerServiceHook(module).install(classLoader)
            }
        }
    }

    private abstract class ImePackageHookSpec(
        override val name: String,
    ) : PackageHookSpec {

        override val feature: HookFeature = HookFeature.Ime

        override fun isSupported(param: PackageReadyParam): Boolean {
            return ImePackageMatcher.matches(param.packageName)
        }
    }

    private object InputMethodServicePackageHookSpec : ImePackageHookSpec("InputMethodService") {
        override fun install(
            module: XposedModule,
            param: PackageReadyParam,
        ) {
            InputMethodServiceHook(module).install(param.classLoader)
        }
    }

    private object NavigationBarControllerPackageHookSpec :
        ImePackageHookSpec("NavigationBarController") {

        override fun install(
            module: XposedModule,
            param: PackageReadyParam,
        ) {
            NavigationBarControllerHook(module).install(param.classLoader)
        }
    }

    private object NavigationBarInflaterPackageHookSpec :
        ImePackageHookSpec("NavigationBarInflaterView") {

        override fun install(
            module: XposedModule,
            param: PackageReadyParam,
        ) {
            NavigationBarInflaterHook(module).install(param.classLoader)
        }
    }

    private object NavigationBarViewPackageHookSpec : ImePackageHookSpec("NavigationBarView") {
        override fun install(
            module: XposedModule,
            param: PackageReadyParam,
        ) {
            NavigationBarViewHook(module).install(param.classLoader)
        }
    }

    private object DeadZonePackageHookSpec : ImePackageHookSpec("DeadZone") {
        override fun install(
            module: XposedModule,
            param: PackageReadyParam,
        ) {
            DeadZoneHook(module).install(param.classLoader)
        }
    }

    private object InputMethodBottomManagerPackageHookSpec :
        ImePackageHookSpec("InputMethodBottomManager") {

        override fun install(
            module: XposedModule,
            param: PackageReadyParam,
        ) {
            InputMethodBottomManagerHook(module).install(param.classLoader)
        }
    }
}