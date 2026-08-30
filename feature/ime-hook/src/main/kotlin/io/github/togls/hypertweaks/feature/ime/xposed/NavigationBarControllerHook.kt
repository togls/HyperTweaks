package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.graphics.Insets
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
    private val sdkInt = context.environment.sdkInt

    private val imePickerShortClickEnabled = AtomicBoolean(false)

    private val settingsSubscriptions = mutableListOf<HookSettingsSubscription>()

    @SuppressLint("PrivateApi")
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, CaptionBarTarget)
        if (sdkInt >= Android17Api) {
            logImeTargetResolveStarted(log, SystemInsetsTarget)
        }
        logImeTargetResolveStarted(log, ImeSwitchTarget)
        val targetClass = resolveTargetClass(classLoader)
            ?: return skippedControllerTargets()

        observeSettings()

        return buildList {
            add(installCaptionBarHeightHook(targetClass))
            if (sdkInt >= Android17Api) {
                add(installSystemInsetsHook(targetClass))
            }
            add(installImeSwitchButtonClickHook(targetClass))
        }
    }

    private fun skippedControllerTargets(): List<ImeTargetInstallResult> = buildList {
        val reason = "NavigationBarController class not found"
        add(skipImeTarget(CaptionBarTarget, reason, log))
        if (sdkInt >= Android17Api) add(skipImeTarget(SystemInsetsTarget, reason, log))
        add(skipImeTarget(ImeSwitchTarget, reason, log))
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
            log.i("hooked ${targetClass.name}#getImeCaptionBarHeight")
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

    private fun installSystemInsetsHook(targetClass: Class<*>): ImeTargetInstallResult {
        val drawsNavBarField = runCatching {
            targetClass.getDeclaredField("mImeDrawsImeNavBar").apply { isAccessible = true }
        }.onFailure { error ->
            log.w("skip system insets hook: mImeDrawsImeNavBar not found", error)
        }.getOrNull()
            ?: return skipImeTarget(SystemInsetsTarget, "mImeDrawsImeNavBar not found", log)

        val captionBarHeightMethod = findCaptionBarHeightMethod(targetClass)
            ?: return skipImeTarget(SystemInsetsTarget, "getImeCaptionBarHeight not found", log)
        val systemInsetsMethod = findSystemInsetsMethod(targetClass)
            ?: return skipImeTarget(SystemInsetsTarget, "getSystemInsets not found", log)

        return installImeTarget(SystemInsetsTarget, log) {
            hookSystemInsets(systemInsetsMethod, captionBarHeightMethod, drawsNavBarField)
            log.i("hooked ${targetClass.name}#getSystemInsets")
        }
    }

    private fun findSystemInsetsMethod(targetClass: Class<*>): Method? {
        return runCatching {
            targetClass.getDeclaredMethod("getSystemInsets").apply {
                require(returnType == Insets::class.java) { "method must return Insets" }
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip system insets hook: getSystemInsets() not found", error)
        }.getOrNull()
    }

    private fun hookSystemInsets(
        method: Method,
        captionBarHeightMethod: Method,
        drawsNavBarField: java.lang.reflect.Field,
    ) {
        engine.hook(method) { chain ->
            val originalResult = chain.proceed()
            preserveOriginalOnFailure(log, SystemInsetsTarget, originalResult) {
                expandSystemInsets(chain, originalResult, captionBarHeightMethod, drawsNavBarField)
            }
        }
    }

    private fun expandSystemInsets(
        chain: HookChain,
        originalResult: Any?,
        captionBarHeightMethod: Method,
        drawsNavBarField: java.lang.reflect.Field,
    ): Any? {
        val originalInsets = originalResult as? Insets ?: return originalResult
        val receiver = chain.thisObject ?: return originalResult
        val drawsNavBar = drawsNavBarField.getBoolean(receiver)
        val captionBarHeight = invokeCaptionBarHeight(captionBarHeightMethod, receiver)
        val replacementBottom = resolveNavigationBarBottomInset(
            drawsNavBar = drawsNavBar,
            originalBottom = originalInsets.bottom,
            captionBarHeight = captionBarHeight,
        ) ?: return originalResult
        if (replacementBottom == originalInsets.bottom) return originalResult
        logExpandedSystemInsets(originalInsets.bottom, captionBarHeight, replacementBottom)
        return Insets.of(originalInsets.left, originalInsets.top, originalInsets.right, replacementBottom)
    }

    private fun invokeCaptionBarHeight(method: Method, receiver: Any): Int? {
        val result = if (method.parameterCount == 0) {
            method.invoke(receiver)
        } else {
            method.invoke(receiver, true)
        }
        return result as? Int
    }

    private fun logExpandedSystemInsets(originalBottom: Int, targetBottom: Int?, resultBottom: Int) {
        log.debug(
            event = "ime.navigation_bar_insets.expanded",
            fields = mapOf(
                "original_bottom_px" to originalBottom.toString(),
                "target_bottom_px" to targetBottom.toString(),
                "result_bottom_px" to resultBottom.toString(),
            ),
        )
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
            log.i("hooked ${targetClass.name}#onImeSwitchButtonClick(View)")
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

    private fun resolveTargetClass(classLoader: ClassLoader): Class<*>? {
        var lastError: Throwable? = null
        for (className in navigationBarControllerClassNames(sdkInt)) {
            val targetClass = runCatching {
                classLoader.loadClass(className)
            }.onFailure { error ->
                lastError = error
            }.getOrNull()
            if (targetClass != null) return targetClass
        }
        log.w("skip NavigationBarControllerHook: class candidates not found", lastError)
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
        private const val SystemInsetsTarget = "navigation_bar_controller.get_system_insets"
        private const val ImeSwitchTarget =
            "navigation_bar_controller.on_ime_switch_button_click"
    }
}

internal fun resolveNavigationBarBottomInset(
    drawsNavBar: Boolean,
    originalBottom: Int?,
    captionBarHeight: Int?,
): Int? {
    if (!drawsNavBar || originalBottom == null || captionBarHeight == null) return originalBottom
    return maxOf(originalBottom, captionBarHeight)
}

internal fun navigationBarControllerClassNames(sdkInt: Int): List<String> {
    val currentClass = "android.inputmethodservice.NavigationBarController"
    val legacyClass = "android.inputmethodservice.NavigationBarController\$Impl"
    // API 37 将旧 Impl 实现折叠进控制器本身，候选顺序仍为旧系统保留回退。
    return if (sdkInt >= Android17Api) {
        listOf(currentClass, legacyClass)
    } else {
        listOf(legacyClass, currentClass)
    }
}

private const val Android17Api = 37
