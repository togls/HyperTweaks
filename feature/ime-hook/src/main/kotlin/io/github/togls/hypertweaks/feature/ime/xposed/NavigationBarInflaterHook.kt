package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.core.xposed.snapshotOrDisabled
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.util.concurrent.atomic.AtomicReference

class NavigationBarInflaterHook(
    context: HookContext
) {

    private val engine = context.engine
    private val log = context.log
    private val initialSettings = context.settings
    private val settingsProvider = context.settingsProvider

    private val navBarLayoutHandle = AtomicReference("")

    private val settingsSubscriptions = mutableListOf<HookSettingsSubscription>()

    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val getDefaultLayoutMethod = findGetDefaultLayoutMethod(classLoader)
            ?: return listOf(
                skipImeTarget(Target, "NavigationBarInflaterView.getDefaultLayout not found", log),
            )

        observeSettings()

        val installResult = installImeTarget(Target, log) {
            engine.hook(getDefaultLayoutMethod, ::interceptDefaultLayout)
            log.i("hooked $TARGET_CLASS_NAME#getDefaultLayout()")
        }
        return listOf(installResult)
    }

    private fun interceptDefaultLayout(chain: HookChain): Any? {
        val originalLayout = chain.proceed() as? String
        val configuredLayout = navBarLayoutHandle.get().trim()
        val bypassReason = navigationBarLayoutBypassReason(configuredLayout, originalLayout)
        if (bypassReason != null) {
            logImeCallbackBypassed(log, Target, bypassReason)
            return originalLayout
        }
        return preserveOriginalOnFailure(log, Target, originalLayout) {
            log.i("replace nav bar layout: $originalLayout -> $configuredLayout")
            configuredLayout
        }
    }

    @SuppressLint("PrivateApi")
    private fun findGetDefaultLayoutMethod(classLoader: ClassLoader) =
        runCatching {
            val targetClass = classLoader.loadClass(TARGET_CLASS_NAME)

            targetClass.getDeclaredMethod("getDefaultLayout").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip NavigationBarInflaterHook: ${error.message}", error)
        }.getOrNull()

    private fun observeSettings() {
        updateLayout(initialSettings)
        settingsSubscriptions += settingsProvider.subscribe { state ->
            updateLayout(state.snapshotOrDisabled())
        }
    }

    private fun updateLayout(settings: HookSettingsSnapshot) {
        val nextLayout = settings.navBarLayoutHandle
        navBarLayoutHandle.set(nextLayout)
        log.i("hook settings updated: nav_bar_layout_handle=$nextLayout")
    }

    private companion object {
        private const val Target = "navigation_bar_inflater.get_default_layout"
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarInflaterView"
    }
}

internal fun resolveNavigationBarLayoutReplacement(
    configuredLayout: String,
    originalLayout: String?,
): String? {
    val bypassReason = navigationBarLayoutBypassReason(configuredLayout, originalLayout)
    return configuredLayout.takeIf { bypassReason == null }
}

internal fun navigationBarLayoutBypassReason(
    configuredLayout: String,
    originalLayout: String?,
): String? {
    if (configuredLayout.isBlank()) return "blank_configuration"
    if (originalLayout == null) return "original_layout_unavailable"
    return "same_as_original".takeIf { configuredLayout == originalLayout }
}
