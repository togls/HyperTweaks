package io.github.togls.hypertweaks.xposed

import android.app.Application
import android.os.Process
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookLogBridgeTransport
import io.github.togls.hypertweaks.core.xposed.HookFeature
import io.github.togls.hypertweaks.core.xposed.HookRegistry
import io.github.togls.hypertweaks.core.xposed.PackageHookSpec
import io.github.togls.hypertweaks.core.xposed.SystemServerHookSpec
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.hook.HookLogRuntime
import io.github.togls.hypertweaks.logging.hook.HookLogBootstrap

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

    private val logBootstrap: HookLogBootstrap by xposedLazy {
        HookLogRuntime.createSafe(
            module = this,
            preferencesProvider = { getRemotePreferences(RemotePreferenceKeys.GroupName) },
            modeKey = RemotePreferenceKeys.LogMode,
            versionKey = RemotePreferenceKeys.LogConfigVersion,
            transport = HookLogBridgeTransport(),
            context = createLogContext(),
        )
    }

    private val rootContext: HookContext by xposedLazy {
        HookContext(
            module = this,
            log = logBootstrap.rootLogger,
        )
    }

    private val log by xposedLazy {
        rootContext.log.child("HyperTweaksModule")
    }

    private val hookRegistry by xposedLazy {
        HookRegistry(rootContext.child("HookRegistry"))
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log.info(
            event = "module.load.succeeded",
            message = "Xposed module loaded",
        )
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        hookRegistry.installSystemServerHooks(param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        hookRegistry.installPackageHooks(param)
    }

    private companion object {
        fun createLogContext(): LogContext {
            return runCatching {
                LogContext(
                    processName = Application.getProcessName(),
                    pid = Process.myPid(),
                    tid = Process.myTid(),
                )
            }.getOrDefault(LogContext())
        }

        fun <T> xposedLazy(
            initializer: () -> T,
        ): Lazy<T> {
            return lazy(
                mode = LazyThreadSafetyMode.NONE,
                initializer = initializer,
            )
        }
    }
}
