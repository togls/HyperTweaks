package io.github.togls.miaospime.xposed

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.togls.miaospime.xposed.hook.DeadZoneHook
import io.github.togls.miaospime.xposed.hook.InputMethodBottomManagerHook
import io.github.togls.miaospime.xposed.hook.InputMethodManagerServiceHook
import io.github.togls.miaospime.xposed.hook.InputMethodManagerServiceImplHook
import io.github.togls.miaospime.xposed.hook.InputMethodServiceHook
import io.github.togls.miaospime.xposed.hook.NavigationBarControllerHook
import io.github.togls.miaospime.xposed.hook.NavigationBarInflaterHook
import io.github.togls.miaospime.xposed.hook.NavigationBarViewHook
import io.github.togls.miaospime.xposed.util.HookLog

class ImeModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookLog.i(this, "onModuleLoaded")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.getClassLoader()

        HookLog.i(this, "onSystemServerStarting")

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            installSystemServerHook("InputMethodManagerService") {
                InputMethodManagerServiceHook(this).install(classLoader)
            }
        } else {
            HookLog.i(
                this,
                "skip InputMethodManagerServiceHook on sdk=${Build.VERSION.SDK_INT}",
            )
        }

        installSystemServerHook("InputMethodManagerServiceImpl") {
            InputMethodManagerServiceImplHook(this).install(classLoader)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage()) {
            return
        }

        val packageName = param.getPackageName()
        val classLoader = param.getClassLoader()

        HookLog.i(this, "onPackageReady package=$packageName")

        installPackageHook("InputMethodService", packageName) {
            InputMethodServiceHook(this).install(classLoader)
        }

        installPackageHook("NavigationBarController", packageName) {
            NavigationBarControllerHook(this).install(classLoader)
        }

        installPackageHook("NavigationBarInflaterView", packageName) {
            NavigationBarInflaterHook(this).install(classLoader)
        }

        installPackageHook("NavigationBarView", packageName) {
            NavigationBarViewHook(this).install(classLoader)
        }

        installPackageHook("DeadZone", packageName) {
            DeadZoneHook(this).install(classLoader)
        }

        installPackageHook("InputMethodBottomManager", packageName) {
            InputMethodBottomManagerHook(this).install(classLoader)
        }
    }

    private fun installSystemServerHook(
        name: String,
        block: () -> Unit,
    ) {
        runCatching(block)
            .onSuccess {
                HookLog.i(this, "$name installed in system_server")
            }
            .onFailure { error ->
                HookLog.e(this, "failed to install $name in system_server", error)
            }
    }

    private fun installPackageHook(
        name: String,
        packageName: String,
        block: () -> Unit,
    ) {
        runCatching(block)
            .onSuccess {
                HookLog.i(this, "$name installed for $packageName")
            }
            .onFailure { error ->
                HookLog.e(this, "failed to install $name for $packageName", error)
            }
    }
}