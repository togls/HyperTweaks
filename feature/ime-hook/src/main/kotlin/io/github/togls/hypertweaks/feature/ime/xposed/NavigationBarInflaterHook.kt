package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.core.xposed.snapshotOrDisabled
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

    fun install(classLoader: ClassLoader) {
        val inflateLayoutMethod = findInflateLayoutMethod(classLoader) ?: return

        observeSettings()

        engine.hook(inflateLayoutMethod) { chain ->
                val configuredLayout = navBarLayoutHandle.get().trim()
                val originalLayout = chain.getArg(0) as? String

                if (configuredLayout.isNotBlank() && originalLayout != null) {
                    if (configuredLayout != originalLayout) {
                        log.i("replace nav bar layout: $originalLayout -> $configuredLayout")
                    }

                    return@hook chain.proceed(
                        arrayOf<Any>(configuredLayout),
                    )
                }

                chain.proceed()
            }

        log.i("hooked $TARGET_CLASS_NAME#inflateLayout(String)")
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
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarInflaterView"
    }
}
