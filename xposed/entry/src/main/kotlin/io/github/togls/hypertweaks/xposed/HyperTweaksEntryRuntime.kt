package io.github.togls.hypertweaks.xposed

import android.app.Application
import android.os.Build
import android.os.Process
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookDispatcher
import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import io.github.togls.hypertweaks.core.xposed.HookLogBridgeTransport
import io.github.togls.hypertweaks.core.xposed.HookRegistry
import io.github.togls.hypertweaks.core.xposed.LibXposedHookEngine
import io.github.togls.hypertweaks.core.xposed.ProcessHookInstallGuard
import io.github.togls.hypertweaks.core.xposed.RemoteHookSettingsProvider
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.hook.HookLogBootstrap
import io.github.togls.hypertweaks.logging.hook.HookLogRuntime
import java.util.UUID

internal class HyperTweaksEntryRuntime(
    private val module: XposedModule,
) {
    private val remotePreferences by lazy(LazyThreadSafetyMode.NONE) {
        module.getRemotePreferences(RemotePreferenceKeys.GroupName)
    }
    private val xposedLogSink = XposedLogSink(module)
    private val logBootstrap: HookLogBootstrap = HookLogRuntime.createSafe(
        xposedSink = xposedLogSink,
        onListenerFailure = { error ->
            module.log(
                android.util.Log.ERROR,
                "HyperTweaks",
                "config.listener.failed",
                error,
            )
        },
        preferencesProvider = {
            remotePreferences
        },
        modeKey = RemotePreferenceKeys.LogMode,
        versionKey = RemotePreferenceKeys.LogConfigVersion,
        recoveryKey = RemotePreferenceKeys.LogBridgeRecoveryGeneration,
        transport = HookLogBridgeTransport(),
        context = createLogContext(),
    )
    private val logger = logBootstrap.rootLogger.child("HyperTweaksEntryRuntime")
    private val settingsProvider = RemoteHookSettingsProvider.create(
        preferencesProvider = {
            remotePreferences
        },
        logger = logger.child("HookSettings"),
    )
    private val registry = HookRegistry(
        HookDispatcher(
            catalog = HyperTweaksHookCatalog.create(),
            engine = LibXposedHookEngine(module),
            settingsProvider = settingsProvider,
            installGuard = ProcessHookInstallGuard(),
            logger = logger.child("HookRegistry"),
        ),
    )

    fun onModuleLoaded(param: ModuleLoadedParam) {
        logger.info(
            event = "module.load.succeeded",
            message = "Xposed module loaded",
            fields = mapOf(
                "process" to param.processName,
                "system_server" to param.isSystemServer.toString(),
            ),
        )
    }

    fun onSystemServerStarting(param: SystemServerStartingParam) {
        registry.install(
            HookEnvironment(
                packageName = SystemServerTarget,
                processName = SystemServerTarget,
                classLoader = param.classLoader,
                sdkInt = Build.VERSION.SDK_INT,
                sessionId = UUID.randomUUID().toString(),
                isSystemServer = true,
            ),
        )
    }

    fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        registry.install(
            HookEnvironment(
                packageName = param.packageName,
                processName = Application.getProcessName(),
                classLoader = param.classLoader,
                sdkInt = Build.VERSION.SDK_INT,
                sessionId = UUID.randomUUID().toString(),
            ),
        )
    }

    private fun createLogContext(): LogContext {
        return runCatching {
            LogContext(
                processName = Application.getProcessName(),
                pid = Process.myPid(),
                tid = Process.myTid(),
            )
        }.getOrElse { error ->
            module.log(
                android.util.Log.WARN,
                "HyperTweaks",
                "Unable to create structured log context",
                error,
            )
            LogContext()
        }
    }

    private companion object {
        const val SystemServerTarget = "system_server"
    }
}
