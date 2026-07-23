package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.core.xposed.snapshotOrDisabled
import io.github.togls.hypertweaks.core.xposed.util.dpToPx
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class NavigationBarControllerHook(
    context: HookContext
) {

    private val engine = context.engine
    private val log = context.log
    private val initialSettings = context.settings
    private val settingsProvider = context.settingsProvider

    private val imePickerShortClickEnabled = AtomicBoolean(false)

    private val settingsSubscriptions = mutableListOf<HookSettingsSubscription>()

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip NavigationBarControllerHook: class not found", error)
        }.getOrNull() ?: return

        observeSettings()

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

        engine.hook(captionBarHeightMethod) { chain ->
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

        engine.hook(clickMethod) { chain ->
                if (!imePickerShortClickEnabled.get()) {
                    return@hook chain.proceed()
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

    private fun observeSettings() {
        updateImePickerEnabled(initialSettings)
        settingsSubscriptions += settingsProvider.subscribe { state ->
            updateImePickerEnabled(state.snapshotOrDisabled())
        }
    }

    private fun updateImePickerEnabled(settings: HookSettingsSnapshot) {
        val enabled = settings.navBarLayoutStart == ImePickerValue ||
            settings.navBarLayoutEnd == ImePickerValue

        imePickerShortClickEnabled.set(enabled)

        log.i("ime picker short click enabled=$enabled")
    }

    private companion object {
        private const val ImePickerValue = "ime_picker"
        private const val TARGET_CLASS_NAME =
            $$"android.inputmethodservice.NavigationBarController$Impl"
    }
}
