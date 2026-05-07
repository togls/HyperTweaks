package io.github.togls.hypertweaks.core.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

interface SystemServerHookSpec {
    val name: String
    val feature: HookFeature

    fun install(
        module: XposedModule,
        classLoader: ClassLoader,
    )
}

interface PackageHookSpec {
    val name: String
    val feature: HookFeature

    fun isSupported(param: PackageReadyParam): Boolean

    fun install(
        module: XposedModule,
        param: PackageReadyParam,
    )
}