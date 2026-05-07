package io.github.togls.hypertweaks.core.xposed

import android.os.Build
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
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
    private val context: HookContext,
) {

    private val module = context.module
    private val log = context.log

    private val installLogger = HookInstallLogger(log)

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
        val target = "system_server"

        installLogger.startSystemServer(target)

        systemServerHooks.forEach { spec ->

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
                    context = context.child(spec.name),
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
                    context = context.child(spec.name),
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
            log.w(
                message = "failed to read feature toggle",
                error = error,
                "feature" to feature.name,
                "key" to feature.preferenceKey,
            )
        }.getOrDefault(false)
    }

    private object KeepAliveSystemServerHookSpec : SystemServerHookSpec {
        override val name: String = "KeepAliveHook"
        override val feature: HookFeature = HookFeature.KeepAlive

        override fun install(
            context: HookContext,
            classLoader: ClassLoader,
        ) {
            KeepAliveHook(context).installSystemServer(classLoader)
        }
    }

    private object OomAdjProtectSystemServerHookSpec : SystemServerHookSpec {
        override val name: String = "OomAdjProtectHook"
        override val feature: HookFeature = HookFeature.KeepAlive

        override fun install(
            context: HookContext,
            classLoader: ClassLoader,
        ) {
            OomAdjProtectHook(context).installSystemServer(classLoader)
        }
    }

    private object ImeSystemServerHookSpec : SystemServerHookSpec {
        override val name: String = "ImeSystemServerHook"
        override val feature: HookFeature = HookFeature.Ime

        override fun install(
            context: HookContext,
            classLoader: ClassLoader,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                InputMethodManagerServiceImplHook(context).install(classLoader)
            } else {
                InputMethodManagerServiceHook(context).install(classLoader)
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
            context: HookContext,
            param: PackageReadyParam,
        ) {
            InputMethodServiceHook(context).install(param.classLoader)
        }
    }

    private object NavigationBarControllerPackageHookSpec :
        ImePackageHookSpec("NavigationBarController") {

        override fun install(
            context: HookContext,
            param: PackageReadyParam,
        ) {
            NavigationBarControllerHook(context).install(param.classLoader)
        }
    }

    private object NavigationBarInflaterPackageHookSpec :
        ImePackageHookSpec("NavigationBarInflaterView") {

        override fun install(
            context: HookContext,
            param: PackageReadyParam,
        ) {
            NavigationBarInflaterHook(context).install(param.classLoader)
        }
    }

    private object NavigationBarViewPackageHookSpec : ImePackageHookSpec("NavigationBarView") {
        override fun install(
            context: HookContext,
            param: PackageReadyParam,
        ) {
            NavigationBarViewHook(context).install(param.classLoader)
        }
    }

    private object DeadZonePackageHookSpec : ImePackageHookSpec("DeadZone") {
        override fun install(
            context: HookContext,
            param: PackageReadyParam,
        ) {
            DeadZoneHook(context).install(param.classLoader)
        }
    }

    private object InputMethodBottomManagerPackageHookSpec :
        ImePackageHookSpec("InputMethodBottomManager") {

        override fun install(
            context: HookContext,
            param: PackageReadyParam,
        ) {
            InputMethodBottomManagerHook(context).install(param.classLoader)
        }
    }
}