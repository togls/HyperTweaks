package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

class InputMethodManagerServiceHook(
    private val context: HookContext,
) {

    private val engine = context.engine
    private val log = context.log

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val serviceClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: class not found", error)
        }.getOrNull() ?: return

        val method = runCatching {
            serviceClass.getDeclaredMethod("getInputMethodNavButtonFlagsLocked").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceHook: getInputMethodNavButtonFlagsLocked not found",
                error,
            )
        }.getOrNull() ?: return

        val imeDrawsImeNavBarResField = runCatching {
            serviceClass.getDeclaredField("mImeDrawsImeNavBarRes").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceHook: mImeDrawsImeNavBarRes not found",
                error
            )
        }.getOrNull() ?: return

        val settingsField = runCatching {
            serviceClass.getDeclaredField("mSettings").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: mSettings not found", error)
        }.getOrNull() ?: return

        val contextField = runCatching {
            serviceClass.getDeclaredField("mContext").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: mContext not found", error)
        }.getOrNull() ?: return

        val wrapperClass = runCatching {
            classLoader.loadClass("com.android.server.inputmethod.OverlayableSystemBooleanResourceWrapper")
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceHook: OverlayableSystemBooleanResourceWrapper not found",
                error,
            )
        }.getOrNull() ?: return

        val valueRefField = runCatching {
            wrapperClass.getDeclaredField("mValueRef").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: mValueRef not found", error)
        }.getOrNull() ?: return

        val serviceStubGetInstanceMethod = runCatching {
            val stubClass =
                classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerServiceStub")

            stubClass.getDeclaredMethod("getInstance").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceHook: InputMethodManagerServiceStub.getInstance not found",
                error,
            )
        }.getOrNull() ?: return

        engine.hook(method) { chain ->
                runCatching {
                    val thisObject = chain.thisObject ?: return@runCatching

                    val context = contextField.get(thisObject) as? Context
                        ?: return@runCatching

                    val settings = settingsField.get(thisObject)
                        ?: return@runCatching

                    val selectedInputMethod = getSelectedInputMethod(settings)
                        ?: return@runCatching

                    val serviceImpl = invokeNoArg(
                        method = serviceStubGetInstanceMethod,
                        receiver = thisObject,
                    ) ?: return@runCatching

                    val isCustomizedInputMethod = isCustomizedInputMethod(
                        serviceImpl = serviceImpl,
                        inputMethodId = selectedInputMethod,
                    ) ?: return@runCatching

                    val isGestureNav = Settings.Secure.getInt(
                        context.contentResolver,
                        NAVIGATION_MODE_KEY,
                        NAVIGATION_MODE_GESTURAL,
                    ) == NAVIGATION_MODE_GESTURAL

                    val canImeDrawImeNavBar = isGestureNav && !isCustomizedInputMethod

                    updateGesturalOverlay(
                        context = context,
                        enabled = canImeDrawImeNavBar,
                    )

                    val imeDrawsImeNavBarRes = imeDrawsImeNavBarResField.get(thisObject)
                        ?: return@runCatching

                    val valueRef = valueRefField.get(imeDrawsImeNavBarRes) as? AtomicBoolean
                        ?: return@runCatching

                    valueRef.set(canImeDrawImeNavBar)
                }.onFailure { error ->
                    log.e("hook getInputMethodNavButtonFlagsLocked failed", error)
                }

                chain.proceed()
            }

        log.i("hooked InputMethodManagerService#getInputMethodNavButtonFlagsLocked")
    }

    private fun getSelectedInputMethod(settings: Any): String? {
        val method = settings.javaClass.findDeclaredMethod(
            name = "getSelectedInputMethod",
            parameterCount = 0,
        ) ?: return null

        return method.invoke(settings) as? String
    }

    private fun isCustomizedInputMethod(
        serviceImpl: Any,
        inputMethodId: String,
    ): Boolean? {
        val method = serviceImpl.javaClass.findDeclaredMethod(
            name = "isCustomizedInputMethod",
            parameterCount = 1,
        ) ?: return null

        return method.invoke(serviceImpl, inputMethodId) as? Boolean
    }

    private fun updateGesturalOverlay(
        context: Context,
        enabled: Boolean,
    ) {
        runCatching {
            val overlayManager = context.getSystemService("overlay")
                ?: return

            val userHandleCurrent = Class.forName("android.os.UserHandle")
                .getDeclaredField("CURRENT")
                .apply {
                    isAccessible = true
                }
                .get(null)

            val getOverlayInfoMethod = overlayManager.javaClass.findDeclaredMethod(
                name = "getOverlayInfo",
                parameterCount = 2,
            ) ?: return

            val overlayInfo = getOverlayInfoMethod.invoke(
                overlayManager,
                NAV_BAR_MODE_GESTURAL_OVERLAY,
                userHandleCurrent,
            ) ?: return

            val isEnabledMethod = overlayInfo.javaClass.findDeclaredMethod(
                name = "isEnabled",
                parameterCount = 0,
            ) ?: return

            val currentEnabled = isEnabledMethod.invoke(overlayInfo) as? Boolean
                ?: return

            if (currentEnabled == enabled) {
                return
            }

            val setEnabledMethod = overlayManager.javaClass.findDeclaredMethod(
                name = "setEnabled",
                parameterCount = 3,
            ) ?: return

            setEnabledMethod.invoke(
                overlayManager,
                NAV_BAR_MODE_GESTURAL_OVERLAY,
                enabled,
                userHandleCurrent,
            )

            log.i("gestural overlay changed: $NAV_BAR_MODE_GESTURAL_OVERLAY=$enabled")
        }.onFailure { error ->
            log.e("failed to toggle gestural overlay", error)
        }
    }

    private fun invokeNoArg(
        method: Method,
        receiver: Any?,
    ): Any? {
        val target = if (Modifier.isStatic(method.modifiers)) {
            null
        } else {
            receiver
        }

        return method.invoke(target)
    }

    private fun Class<*>.findDeclaredMethod(
        name: String,
        parameterCount: Int,
    ): Method? {
        var current: Class<*>? = this

        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.size == parameterCount
            }

            if (method != null) {
                method.isAccessible = true
                return method
            }

            current = current.superclass
        }

        return null
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "com.android.server.inputmethod.InputMethodManagerService"

        private const val NAVIGATION_MODE_KEY = "navigation_mode"
        private const val NAVIGATION_MODE_GESTURAL = 2

        private const val NAV_BAR_MODE_GESTURAL_OVERLAY =
            "com.android.internal.systemui.navbar.gestural"
    }
}
