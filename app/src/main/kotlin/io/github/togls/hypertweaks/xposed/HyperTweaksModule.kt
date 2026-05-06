package io.github.togls.hypertweaks.xposed

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.togls.hypertweaks.xposed.hook.DeadZoneHook
import io.github.togls.hypertweaks.xposed.hook.InputMethodBottomManagerHook
import io.github.togls.hypertweaks.xposed.hook.InputMethodManagerServiceHook
import io.github.togls.hypertweaks.xposed.hook.InputMethodManagerServiceImplHook
import io.github.togls.hypertweaks.xposed.hook.InputMethodServiceHook
import io.github.togls.hypertweaks.xposed.hook.KeepAliveHook
import io.github.togls.hypertweaks.xposed.hook.NavigationBarControllerHook
import io.github.togls.hypertweaks.xposed.hook.NavigationBarInflaterHook
import io.github.togls.hypertweaks.xposed.hook.NavigationBarViewHook
import io.github.togls.hypertweaks.xposed.hook.OomAdjProtectHook
import io.github.togls.hypertweaks.xposed.util.HookLog

class HyperTweaksModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookLog.i(this, "onModuleLoaded")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader

        HookLog.i(this, "========== HyperTweaks system_server loaded ==========")

        installImeSystemServerHooks(classLoader)

        installSystemServerHook("KeepAliveHook") {
            KeepAliveHook(this).installSystemServer(classLoader)
        }

        installSystemServerHook("OomAdjProtectHook") {
            OomAdjProtectHook(this).installSystemServer(classLoader)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) {
            return
        }

        val packageName = param.packageName
        val classLoader = param.classLoader

        HookLog.i(this, "onPackageReady package=$packageName")

        if (!isImePackage(packageName)) {
            HookLog.i(this, "skip IME hooks for non-IME package=$packageName")
            return
        }

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

    private fun installImeSystemServerHooks(classLoader: ClassLoader) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> {
                installSystemServerHook("InputMethodManagerServiceImpl") {
                    InputMethodManagerServiceImplHook(this).install(classLoader)
                }
            }

            else -> {
                installSystemServerHook("InputMethodManagerService") {
                    InputMethodManagerServiceHook(this).install(classLoader)
                }
            }
        }
    }

    private fun isImePackage(packageName: String): Boolean {
        return packageName in setOf(
            "com.google.android.inputmethod.latin",
            "com.baidu.input",
            "com.baidu.input_mi",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.iflytek.inputmethod",
            "com.iflytek.inputmethod.miui",
            "com.tencent.wetype",
            "keepass2android.keepass2android",
        )
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