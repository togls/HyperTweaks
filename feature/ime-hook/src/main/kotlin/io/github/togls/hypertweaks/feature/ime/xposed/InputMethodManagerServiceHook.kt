package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

class InputMethodManagerServiceHook(
    private val context: HookContext,
) {

    private val engine = context.engine
    private val log = context.log

    @SuppressLint("PrivateApi")
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val target = resolveTarget(classLoader)
            ?: return skipped("InputMethodManagerService required members not found")
        val installResult = installImeTarget(Target, log) {
            installHook(target)
            log.i("hooked InputMethodManagerService#getInputMethodNavButtonFlagsLocked")
        }
        return listOf(installResult)
    }

    @SuppressLint("PrivateApi")
    private fun resolveTarget(classLoader: ClassLoader): ResolvedTarget? {
        val serviceClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: class not found", error)
        }.getOrNull() ?: return null
        val serviceMembers = resolveServiceMembers(serviceClass) ?: return null
        val miuiMembers = resolveMiuiMembers(classLoader) ?: return null
        return ResolvedTarget(serviceMembers, miuiMembers)
    }

    private fun resolveServiceMembers(serviceClass: Class<*>): ServiceMembers? {
        val hookMethod = resolveMethod(serviceClass, "getInputMethodNavButtonFlagsLocked")
            ?: return null
        val imeDrawsImeNavBarResField = runCatching {
            serviceClass.getDeclaredField("mImeDrawsImeNavBarRes").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceHook: mImeDrawsImeNavBarRes not found",
                error
            )
        }.getOrNull() ?: return null
        val settingsField = resolveField(serviceClass, "mSettings") ?: return null
        val contextField = resolveField(serviceClass, "mContext") ?: return null
        return ServiceMembers(
            hookMethod = hookMethod,
            imeDrawsImeNavBarResField = imeDrawsImeNavBarResField,
            settingsField = settingsField,
            contextField = contextField,
        )
    }

    @SuppressLint("PrivateApi")
    private fun resolveMiuiMembers(classLoader: ClassLoader): MiuiMembers? {
        val wrapperClass = runCatching {
            classLoader.loadClass("com.android.server.inputmethod.OverlayableSystemBooleanResourceWrapper")
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceHook: OverlayableSystemBooleanResourceWrapper not found",
                error,
            )
        }.getOrNull() ?: return null
        val valueRefField = resolveField(wrapperClass, "mValueRef") ?: return null
        val stubGetInstanceMethod = runCatching {
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
        }.getOrNull() ?: return null
        return MiuiMembers(valueRefField, stubGetInstanceMethod)
    }

    private fun installHook(target: ResolvedTarget) {
        engine.hook(target.service.hookMethod) { chain ->
            preserveOriginalOnFailure(log, Target, Unit) {
                updateNavButtonState(chain, target)
            }
            chain.proceed()
        }
    }

    private fun updateNavButtonState(chain: HookChain, target: ResolvedTarget) {
        val receiver = chain.thisObject ?: return
        val androidContext = target.service.contextField.get(receiver) as? Context ?: return
        val settings = target.service.settingsField.get(receiver) ?: return
        val selectedInputMethod = getSelectedInputMethod(settings) ?: return
        val serviceImpl = invokeNoArg(target.miui.stubGetInstanceMethod, receiver) ?: return
        val isCustomized = isCustomizedInputMethod(serviceImpl, selectedInputMethod) ?: return
        val isGestureNav = Settings.Secure.getInt(
            androidContext.contentResolver,
            NAVIGATION_MODE_KEY,
            NAVIGATION_MODE_GESTURAL,
        ) == NAVIGATION_MODE_GESTURAL
        val canDrawNavBar = isGestureNav && !isCustomized
        updateGesturalOverlay(androidContext, canDrawNavBar)
        val resource = target.service.imeDrawsImeNavBarResField.get(receiver) ?: return
        val valueRef = target.miui.valueRefField.get(resource) as? AtomicBoolean ?: return
        valueRef.set(canDrawNavBar)
    }

    private fun resolveMethod(targetClass: Class<*>, name: String): Method? {
        return runCatching {
            targetClass.getDeclaredMethod(name).apply { isAccessible = true }
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: $name not found", error)
        }.getOrNull()
    }

    private fun resolveField(targetClass: Class<*>, name: String): Field? {
        return runCatching {
            targetClass.getDeclaredField(name).apply { isAccessible = true }
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceHook: $name not found", error)
        }.getOrNull()
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
            val target = resolveOverlayTarget(context) ?: return
            val currentEnabled = target.isEnabledMethod.invoke(target.overlayInfo) as? Boolean
                ?: return
            if (currentEnabled == enabled) return
            target.setEnabledMethod.invoke(
                target.manager,
                NAV_BAR_MODE_GESTURAL_OVERLAY,
                enabled,
                target.userHandle,
            )
            log.i("gestural overlay changed: $NAV_BAR_MODE_GESTURAL_OVERLAY=$enabled")
        }.onFailure { error ->
            log.e("failed to toggle gestural overlay", error)
        }
    }

    private fun resolveOverlayTarget(context: Context): OverlayTarget? {
        val manager = context.getSystemService("overlay") ?: return null
        val userHandle = Class.forName("android.os.UserHandle")
            .getDeclaredField("CURRENT")
            .apply { isAccessible = true }
            .get(null) ?: return null
        val getInfo = manager.javaClass.findDeclaredMethod("getOverlayInfo", 2) ?: return null
        val overlayInfo = getInfo.invoke(manager, NAV_BAR_MODE_GESTURAL_OVERLAY, userHandle)
            ?: return null
        val isEnabled = overlayInfo.javaClass.findDeclaredMethod("isEnabled", 0) ?: return null
        val setEnabled = manager.javaClass.findDeclaredMethod("setEnabled", 3) ?: return null
        return OverlayTarget(manager, userHandle, overlayInfo, isEnabled, setEnabled)
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

    private fun skipped(reason: String): List<ImeTargetInstallResult> {
        return listOf(skipImeTarget(Target, reason, log))
    }

    private data class ResolvedTarget(
        val service: ServiceMembers,
        val miui: MiuiMembers,
    )

    private data class ServiceMembers(
        val hookMethod: Method,
        val imeDrawsImeNavBarResField: Field,
        val settingsField: Field,
        val contextField: Field,
    )

    private data class MiuiMembers(
        val valueRefField: Field,
        val stubGetInstanceMethod: Method,
    )

    private data class OverlayTarget(
        val manager: Any,
        val userHandle: Any,
        val overlayInfo: Any,
        val isEnabledMethod: Method,
        val setEnabledMethod: Method,
    )

    private companion object {
        private const val Target =
            "input_method_manager_service.get_input_method_nav_button_flags_locked"
        private const val TARGET_CLASS_NAME =
            "com.android.server.inputmethod.InputMethodManagerService"

        private const val NAVIGATION_MODE_KEY = "navigation_mode"
        private const val NAVIGATION_MODE_GESTURAL = 2

        private const val NAV_BAR_MODE_GESTURAL_OVERLAY =
            "com.android.internal.systemui.navbar.gestural"
    }
}
