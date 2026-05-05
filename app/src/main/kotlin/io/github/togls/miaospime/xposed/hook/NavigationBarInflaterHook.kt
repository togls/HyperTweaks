package io.github.togls.miaospime.xposed.hook

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.miaospime.data.RemotePreferenceKeys
import java.util.concurrent.atomic.AtomicReference

class NavigationBarInflaterHook(
    private val module: XposedModule,
) {

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
                        logInfo("replace nav bar layout: $originalLayout -> $configuredLayout")
                    }

                    return@intercept chain.proceed(
                        arrayOf<Any>(configuredLayout),
                    )
                }

                chain.proceed()
            }

        logInfo("hooked $TARGET_CLASS_NAME#inflateLayout(String)")
    }

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
            logWarn("skip NavigationBarInflaterHook: ${error.message}", error)
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

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key != RemotePreferenceKeys.NavBarLayoutHandle) {
                    return@OnSharedPreferenceChangeListener
                }

                val nextLayout = sharedPreferences.getString(
                    RemotePreferenceKeys.NavBarLayoutHandle,
                    "",
                ).orEmpty()

                navBarLayoutHandle.set(nextLayout)

                logInfo("remote preference changed: $key=$nextLayout")
            }

            preferenceListeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { error ->
            logWarn("failed to read remote preferences: ${error.message}", error)
        }
    }

    private fun logInfo(message: String) {
        module.log(Log.INFO, TAG, message)
    }

    private fun logWarn(
        message: String,
        error: Throwable? = null,
    ) {
        if (error == null) {
            module.log(Log.WARN, TAG, message)
            return
        }

        module.log(Log.WARN, TAG, message, error)
    }

    private companion object {
        private const val TAG = "MiAospIme"
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarInflaterView"
    }
}