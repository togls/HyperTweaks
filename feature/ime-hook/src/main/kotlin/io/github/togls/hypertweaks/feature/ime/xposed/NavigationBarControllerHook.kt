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
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
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
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, CaptionBarTarget)
        logImeTargetResolveStarted(log, ImeSwitchTarget)
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip NavigationBarControllerHook: class not found", error)
        }.getOrNull() ?: return listOf(
            skipImeTarget(
                CaptionBarTarget,
                "NavigationBarController.Impl class not found",
                log,
            ),
            skipImeTarget(ImeSwitchTarget, "NavigationBarController.Impl class not found", log),
        )

        observeSettings()

        return listOf(
            installCaptionBarHeightHook(targetClass),
            installImeSwitchButtonClickHook(targetClass),
        )
    }

    private fun installCaptionBarHeightHook(targetClass: Class<*>): ImeTargetInstallResult {
        val imeDrawsImeNavBarField = runCatching {
            targetClass.getDeclaredField("mImeDrawsImeNavBar").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip caption bar hook: mImeDrawsImeNavBar not found", error)
        }.getOrNull()
            ?: return skipImeTarget(CaptionBarTarget, "mImeDrawsImeNavBar not found", log)

        val serviceField = runCatching {
            targetClass.getDeclaredField("mService").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip caption bar hook: mService not found", error)
        }.getOrNull() ?: return skipImeTarget(CaptionBarTarget, "mService not found", log)

        val captionBarHeightMethod = findCaptionBarHeightMethod(targetClass)
            ?: run {
                log.w("skip caption bar hook: getImeCaptionBarHeight method not found")
                return skipImeTarget(CaptionBarTarget, "getImeCaptionBarHeight not found", log)
            }

        return installImeTarget(CaptionBarTarget, log) {
            hookCaptionBarHeight(
                captionBarHeightMethod,
                imeDrawsImeNavBarField,
                serviceField,
            )
            log.i($$"hooked NavigationBarController$Impl#getImeCaptionBarHeight")
        }
    }

    private fun hookCaptionBarHeight(
        method: Method,
        imeDrawsImeNavBarField: java.lang.reflect.Field,
        serviceField: java.lang.reflect.Field,
    ) {
        engine.hook(method) { chain ->
            val replacementHeight = preserveOriginalOnFailure(
                log = log,
                event = CaptionBarTarget,
                originalValue = null,
            ) {
                val receiver = chain.thisObject ?: return@preserveOriginalOnFailure null
                val drawsNavBar = (chain.args.firstOrNull() as? Boolean)
                    ?: imeDrawsImeNavBarField.getBoolean(receiver)
                if (!drawsNavBar) return@preserveOriginalOnFailure null
                val service = serviceField.get(receiver) as? InputMethodService
                    ?: return@preserveOriginalOnFailure null
                dpToPx(48, service.resources)
            }
            replacementHeight ?: chain.proceed()
        }
    }

    private fun installImeSwitchButtonClickHook(targetClass: Class<*>): ImeTargetInstallResult {
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
        }.getOrNull()
            ?: return skipImeTarget(
                ImeSwitchTarget,
                "onImeSwitchButtonClick(View) not found",
                log,
            )

        return installImeTarget(ImeSwitchTarget, log) {
            hookImeSwitchButtonClick(clickMethod)
            log.i($$"hooked NavigationBarController$Impl#onImeSwitchButtonClick(View)")
        }
    }

    private fun hookImeSwitchButtonClick(method: Method) {
        engine.hook(method) { chain ->
            if (!imePickerShortClickEnabled.get()) {
                logImeCallbackBypassed(log, ImeSwitchTarget, "ime_picker_short_click_disabled")
                return@hook chain.proceed()
            }
            val handled = preserveOriginalOnFailure(
                log = log,
                event = ImeSwitchTarget,
                originalValue = false,
            ) {
                val view = chain.getArg(0) as? View
                    ?: return@preserveOriginalOnFailure false
                val manager = view.context.getSystemService(InputMethodManager::class.java)
                    ?: return@preserveOriginalOnFailure false
                manager.showInputMethodPicker()
                log.i("show input method picker from IME switch short click")
                true
            }
            if (handled) null else chain.proceed()
        }
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
        private const val CaptionBarTarget = "navigation_bar_controller.get_ime_caption_bar_height"
        private const val ImeSwitchTarget =
            "navigation_bar_controller.on_ime_switch_button_click"
        private const val TARGET_CLASS_NAME =
            $$"android.inputmethodservice.NavigationBarController$Impl"
    }
}
