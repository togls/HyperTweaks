package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.util.dpToPx
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class NavigationBarControllerHook(
    context: HookContext
) {

    private val module = context.module
    private val log = context.log

    private val imePickerShortClickEnabled = AtomicBoolean(false)

    private val preferenceListeners =
        mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip NavigationBarControllerHook: class not found", error)
        }.getOrNull() ?: return

        loadRemotePreferences()

        installCaptionBarHeightHook(targetClass)
        installImeSwitchButtonClickHook(targetClass)

        log.i("NavigationBarControllerHook installed")
    }

    private fun installCaptionBarHeightHook(targetClass: Class<*>) {
        val imeDrawsImeNavBarField = runCatching {
            targetClass.getDeclaredField("mImeDrawsImeNavBar").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip caption bar hook: mImeDrawsImeNavBar not found", error)
        }.getOrNull() ?: return

        val serviceField = runCatching {
            targetClass.getDeclaredField("mService").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip caption bar hook: mService not found", error)
        }.getOrNull() ?: return

        val captionBarHeightMethod = findCaptionBarHeightMethod(targetClass)
            ?: run {
                log.w("skip caption bar hook: getImeCaptionBarHeight method not found")
                return
            }

        module.hook(captionBarHeightMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                runCatching {
                    val thisObject = chain.thisObject ?: return@runCatching null

                    val args = chain.args
                    val imeShouldShowImeNavBar = if (args.isNotEmpty() && args[0] is Boolean) {
                        args[0] as Boolean
                    } else {
                        imeDrawsImeNavBarField.getBoolean(thisObject)
                    }

                    if (!imeShouldShowImeNavBar) {
                        return@runCatching null
                    }

                    val service = serviceField.get(thisObject) as? InputMethodService
                        ?: return@runCatching null

                    dpToPx(48, service.resources)
                }.onFailure { error ->
                    log.e("hook getImeCaptionBarHeight failed", error)
                }.getOrNull()
                    ?: chain.proceed()
            }

        log.i($$"hooked NavigationBarController$Impl#getImeCaptionBarHeight")
    }

    private fun installImeSwitchButtonClickHook(targetClass: Class<*>) {
        val clickMethod = runCatching {
            targetClass.getDeclaredMethod(
                "onImeSwitchButtonClick",
                View::class.java,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip IME picker short-click hook: onImeSwitchButtonClick(View) not found",
                error,
            )
        }.getOrNull() ?: return

        module.hook(clickMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                if (!imePickerShortClickEnabled.get()) {
                    return@intercept chain.proceed()
                }

                runCatching {
                    val view = chain.getArg(0) as? View
                        ?: return@runCatching false

                    val inputMethodManager =
                        view.context.getSystemService(InputMethodManager::class.java)
                            ?: return@runCatching false

                    inputMethodManager.showInputMethodPicker()

                    log.i("show input method picker from IME switch short click")

                    true
                }.onFailure { error ->
                    log.e("show input method picker failed", error)
                }.getOrDefault(false)

                null
            }

        log.i($$"hooked NavigationBarController$Impl#onImeSwitchButtonClick(View)")
    }

    private fun findCaptionBarHeightMethod(targetClass: Class<*>): Method? {
        val booleanType = Boolean::class.javaPrimitiveType ?: return null

        val preferredMethods = if (Build.VERSION.SDK_INT >= 36) {
            listOf(
                arrayOf<Class<*>>(booleanType),
                emptyArray(),
            )
        } else {
            listOf(
                emptyArray(),
                arrayOf<Class<*>>(booleanType),
            )
        }

        for (params in preferredMethods) {
            val method = runCatching {
                targetClass.getDeclaredMethod("getImeCaptionBarHeight", *params).apply {
                    isAccessible = true
                }
            }.getOrNull()

            if (method != null) {
                return method
            }
        }

        return null
    }

    private fun loadRemotePreferences() {
        runCatching {
            val prefs = module.getRemotePreferences(RemotePreferenceKeys.GroupName)

            updateImePickerEnabled(prefs)

            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (
                        key != RemotePreferenceKeys.NavBarLayoutStart &&
                        key != RemotePreferenceKeys.NavBarLayoutEnd
                    ) {
                        return@OnSharedPreferenceChangeListener
                    }

                    updateImePickerEnabled(sharedPreferences)
                }

            preferenceListeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { error ->
            log.w("failed to read remote preferences for IME picker mode", error)
        }
    }

    private fun updateImePickerEnabled(prefs: SharedPreferences) {
        val start = prefs.getString(
            RemotePreferenceKeys.NavBarLayoutStart,
            NavBarButton.Back.value,
        )

        val end = prefs.getString(
            RemotePreferenceKeys.NavBarLayoutEnd,
            NavBarButton.ImeSwitcher.value,
        )

        val enabled = start == NavBarButton.ImePicker.value ||
            end == NavBarButton.ImePicker.value

        imePickerShortClickEnabled.set(enabled)

        log.i("ime picker short click enabled=$enabled")
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            $$"android.inputmethodservice.NavigationBarController$Impl"
    }
}