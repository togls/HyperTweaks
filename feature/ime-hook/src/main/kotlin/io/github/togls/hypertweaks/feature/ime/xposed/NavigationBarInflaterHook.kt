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
        val inflateLayoutMethod = findInflateLayoutMethod(classLoader)
            ?: return listOf(
                skipImeTarget(Target, "NavigationBarInflaterView.inflateLayout not found", log),
            )

        observeSettings()

        val installResult = installImeTarget(Target, log) {
            engine.hook(inflateLayoutMethod) { chain ->
                val replacement = preserveOriginalOnFailure(
                    log = log,
                    event = "NavigationBarInflaterView.inflateLayout",
                    originalValue = null,
                ) {
                    resolveReplacementLayout(chain.getArg(0) as? String)
                }
                replacement?.let { layout ->
                    chain.proceed(arrayOf<Any>(layout))
                } ?: chain.proceed()
            }
            log.i("hooked $TARGET_CLASS_NAME#inflateLayout(String)")
        }
        return listOf(installResult)
    }

    private fun resolveReplacementLayout(originalLayout: String?): String? {
        val configuredLayout = navBarLayoutHandle.get().trim()
        if (configuredLayout.isBlank() || originalLayout == null) return null
        if (configuredLayout != originalLayout) {
            log.i("replace nav bar layout: $originalLayout -> $configuredLayout")
        }
        return configuredLayout
    }

    @SuppressLint("PrivateApi")
    private fun findInflateLayoutMethod(classLoader: ClassLoader) =
        runCatching {
            val targetClass = classLoader.loadClass(TARGET_CLASS_NAME)

            targetClass.getDeclaredMethod(
                "inflateLayout",
                String::class.java,
            ).apply {
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
        private const val Target = "navigation_bar_inflater.inflate_layout"
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarInflaterView"
    }
}
