package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class InputMethodServiceHook(
    context: HookContext
) {

    private val engine = context.engine
    private val log = context.log

    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val inputMethodServiceClass = resolveTargetClass(classLoader)
            ?: return skipped("InputMethodService class not found")
        val hideImeRenderMethod = resolveHideImeRenderMethod(inputMethodServiceClass)
            ?: return skipped("InputMethodService.hideImeRenderGesturalNavButtons not found")
        val canImeRenderMethod = resolveCanImeRenderMethod(inputMethodServiceClass)
            ?: return skipped("InputMethodService.canImeRenderGesturalNavButtons not found")

        val installResult = installImeTarget(Target, log) {
            installHideImeRenderHook(
                classLoader,
                hideImeRenderMethod,
                canImeRenderMethod,
            )
            log.i("hooked InputMethodService#hideImeRenderGesturalNavButtons(String)")
        }
        return listOf(installResult)
    }

    private fun resolveTargetClass(classLoader: ClassLoader): Class<*>? {
        return runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip InputMethodServiceHook: class not found", error)
        }.getOrNull()
    }

    private fun resolveHideImeRenderMethod(targetClass: Class<*>): Method? {
        return runCatching {
            targetClass.getDeclaredMethod(
                "hideImeRenderGesturalNavButtons",
                String::class.java,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodServiceHook: hideImeRenderGesturalNavButtons(String) not found",
                error,
            )
        }.getOrNull()
    }

    private fun resolveCanImeRenderMethod(targetClass: Class<*>): Method? {
        return runCatching {
            targetClass.getDeclaredMethod("canImeRenderGesturalNavButtons").apply {
                require(Modifier.isStatic(modifiers)) { "method must be static" }
                require(returnType == Boolean::class.javaPrimitiveType) {
                    "method must return boolean"
                }
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodServiceHook: canImeRenderGesturalNavButtons() not found",
                error,
            )
        }.getOrNull()
    }

    private fun installHideImeRenderHook(
        classLoader: ClassLoader,
        hideImeRenderMethod: Method,
        canImeRenderMethod: Method,
    ) {
        engine.hook(hideImeRenderMethod) { chain ->
            val replacement = preserveOriginalOnFailure(
                log = log,
                event = CallbackEvent,
                originalValue = null,
            ) {
                resolveHideImeRenderReplacement(
                    isImeSupported = resolveImeSupport(classLoader, chain.thisObject),
                    canImeRender = callCanImeRender(canImeRenderMethod),
                )
            }
            replacement?.also { shouldHide ->
                log.debug(
                    event = "ime.render_gestural_nav_buttons.overridden",
                    fields = mapOf("hide_nav_buttons" to shouldHide.toString()),
                )
            } ?: chain.proceed()
        }
    }

    private fun resolveImeSupport(classLoader: ClassLoader, receiver: Any?): Boolean? {
        val inputMethodService = receiver as? InputMethodService ?: return null
        val stub = loadInputMethodServiceStub(classLoader, receiver) ?: return null
        return callIsImeSupport(stub, inputMethodService.applicationContext)
    }

    private fun callCanImeRender(method: Method): Boolean? {
        return method.invoke(null) as? Boolean
    }

    @SuppressLint("PrivateApi")
    private fun loadInputMethodServiceStub(
        classLoader: ClassLoader,
        receiver: Any,
    ): Any? {
        val stubClass = classLoader.loadClass("android.inputmethodservice.InputMethodServiceStub")

        val getInstanceMethod = stubClass.getDeclaredMethod("getInstance").apply {
            isAccessible = true
        }

        return invokeNoArg(
            method = getInstanceMethod,
            receiver = receiver,
        )
    }

    private fun callIsImeSupport(
        stub: Any,
        context: Context,
    ): Boolean? {
        val method = stub.javaClass.getDeclaredMethod(
            "isImeSupport",
            Context::class.java,
        ).apply {
            isAccessible = true
        }

        return method.invoke(stub, context) as? Boolean
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

    private fun skipped(reason: String): List<ImeTargetInstallResult> {
        return listOf(skipImeTarget(Target, reason, log))
    }

    private companion object {
        private const val Target = "input_method_service.hide_ime_render_gestural_nav_buttons"
        private const val CallbackEvent =
            "InputMethodService.hideImeRenderGesturalNavButtons"
        private const val TARGET_CLASS_NAME = "android.inputmethodservice.InputMethodService"
    }
}

internal fun resolveHideImeRenderReplacement(
    isImeSupported: Boolean?,
    canImeRender: Boolean?,
): Boolean? {
    // API 37 将区域标记改为 static final；直接复用框架能力判断可避免修改全局常量。
    if (isImeSupported != false) return null
    return canImeRender?.not()
}
