package io.github.togls.hypertweaks.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookRegistry
import io.github.togls.hypertweaks.core.xposed.PackageHookSpec
import io.github.togls.hypertweaks.core.xposed.SystemServerHookSpec
import io.github.togls.hypertweaks.core.xposed.util.HookLog

/**
 * Main Xposed module entry point.
 *
 * This class should only forward Xposed lifecycle callbacks to [HookRegistry].
 * It should not contain feature-specific hook installation logic.
 *
 * To add a new feature:
 * 1. Add a new [HookFeature] entry and bind it to its remote preference key.
 * 2. Create one or more HookSpec implementations for system_server[SystemServerHookSpec] or package hooks[PackageHookSpec].
 * 3. Register the new hook specs in the corresponding HookRegistry list[HookRegistry].
 */
class HyperTweaksModule : XposedModule() {

    private val hookRegistry by lazy {
        HookRegistry(this)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookLog.i(this, "onModuleLoaded")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        hookRegistry.installSystemServerHooks(param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        hookRegistry.installPackageHooks(param)
    }
}