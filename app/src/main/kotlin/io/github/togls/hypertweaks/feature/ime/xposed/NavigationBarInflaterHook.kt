package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.util.concurrent.atomic.AtomicReference

class NavigationBarInflaterHook(
    context: HookContext
) {

    private val module = context.module
    private val log = context.log

    private val navBarLayoutHandle = AtomicReference("")

    private val preferenceListeners =
        mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    fun install(classLoader: ClassLoader) {
        val inflateLayoutMethod = findInflateLayoutMethod(classLoader) ?: return

        loadRemotePreferences()

        module.hook(inflateLayoutMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val configuredLayout = navBarLayoutHandle.get().trim()
                val originalLayout = chain.getArg(0) as? String

                if (configuredLayout.isNotBlank() && originalLayout != null) {
                    if (configuredLayout != originalLayout) {
                        log.i("replace nav bar layout: $originalLayout -> $configuredLayout")
                    }

                    return@intercept chain.proceed(
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

    private fun loadRemotePreferences() {
        runCatching {
            val prefs = module.getRemotePreferences(RemotePreferenceKeys.GroupName)

            navBarLayoutHandle.set(
                prefs.getString(
                    RemotePreferenceKeys.NavBarLayoutHandle,
                    "",
                ).orEmpty(),
            )

            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key != RemotePreferenceKeys.NavBarLayoutHandle) {
                        return@OnSharedPreferenceChangeListener
                    }

                    val nextLayout = sharedPreferences.getString(
                        RemotePreferenceKeys.NavBarLayoutHandle,
                        "",
                    ).orEmpty()

                    navBarLayoutHandle.set(nextLayout)

                    log.i("remote preference changed: $key=$nextLayout")
                }

            preferenceListeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { error ->
            log.w("failed to read remote preferences: ${error.message}", error)
        }
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarInflaterView"
    }
}
