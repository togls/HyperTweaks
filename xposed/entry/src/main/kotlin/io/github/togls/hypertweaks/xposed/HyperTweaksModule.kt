package io.github.togls.hypertweaks.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class HyperTweaksModule : XposedModule() {
    private val runtime: HyperTweaksEntryRuntime by lazy(LazyThreadSafetyMode.NONE) {
        HyperTweaksEntryRuntime(this)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        runtime.onModuleLoaded(param)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        runtime.onSystemServerStarting(param)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        runtime.onPackageReady(param)
    }
}
