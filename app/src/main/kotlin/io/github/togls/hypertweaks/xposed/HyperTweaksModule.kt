package io.github.togls.hypertweaks.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.togls.hypertweaks.core.xposed.HookRegistry
import io.github.togls.hypertweaks.core.xposed.util.HookLog

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