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
        HookLog.i(module, "========== HyperTweaks system_server loaded ==========")

        systemServerHooks.forEach { spec ->
            if (!isFeatureEnabled(spec.feature)) {
                HookLog.i(
                    module,
                    "skip ${spec.name} in system_server: feature=${spec.feature} disabled",
                )
                return@forEach
            }

            installSystemServerHook(
                name = spec.name,
                block = {
                    spec.install(
                        module = module,
                        classLoader = classLoader,
                    )
                },
            )
        }
    }

    fun installPackageHooks(param: PackageReadyParam) {
        if (!param.isFirstPackage) {
            return
        }

        val packageName = param.packageName

        HookLog.i(module, "onPackageReady package=$packageName")

        packageHooks.forEach { spec ->
            if (!spec.isSupported(param)) {
                HookLog.i(
                    module,
                    "skip ${spec.name} for unsupported package=$packageName",
                )
                return@forEach
            }

            if (!isFeatureEnabled(spec.feature)) {
                HookLog.i(
                    module,
                    "skip ${spec.name} for $packageName: feature=${spec.feature} disabled",
                )
                return@forEach
            }

            if (!spec.isSupported(param)) {
                return@forEach
            }

            installPackageHook(
                name = spec.name,
                packageName = packageName,
                block = {
                    spec.install(
                        module = module,
                        param = param,
                    )
                },
            )
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

    private fun installSystemServerHook(
        name: String,
        block: () -> Unit,
    ) {
        runCatching(block)
            .onSuccess {
                HookLog.i(module, "$name installed in system_server")
            }
            .onFailure { error ->
                HookLog.e(module, "failed to install $name in system_server", error)
            }
    }

    private fun installPackageHook(
        name: String,
        packageName: String,
        block: () -> Unit,
    ) {
        runCatching(block)
            .onSuccess {
                HookLog.i(module, "$name installed for $packageName")
            }
            .onFailure { error ->
                HookLog.e(module, "failed to install $name for $packageName", error)
            }
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