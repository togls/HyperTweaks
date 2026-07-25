package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.lang.reflect.Field
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
        val internationalBuildField =
            resolveInternationalBuildField(inputMethodServiceClass)
                ?: return skipped("InputMethodService.IS_INTERNATIONAL_BUILD not found")
        val hideImeRenderMethod = resolveHideImeRenderMethod(inputMethodServiceClass)
            ?: return skipped("InputMethodService.hideImeRenderGesturalNavButtons not found")

        val installResult = installImeTarget(Target, log) {
            installHideImeRenderHook(
                classLoader,
                internationalBuildField,
                hideImeRenderMethod,
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

    private fun resolveInternationalBuildField(targetClass: Class<*>): Field? {
        return runCatching {
            targetClass.getDeclaredField("IS_INTERNATIONAL_BUILD").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodServiceHook: IS_INTERNATIONAL_BUILD not found",
                error,
            )
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

    private fun installHideImeRenderHook(
        classLoader: ClassLoader,
        internationalBuildField: Field,
        hideImeRenderMethod: Method,
    ) {
        engine.hook(hideImeRenderMethod) { chain ->
            preserveOriginalOnFailure(log, "InputMethodService.hideImeRenderGesturalNavButtons", Unit) {
                val thisObject = chain.thisObject ?: return@preserveOriginalOnFailure
                val inputMethodService =
                    thisObject as? InputMethodService ?: return@preserveOriginalOnFailure

                val stub = loadInputMethodServiceStub(
                    classLoader = classLoader,
                    receiver = thisObject,
                ) ?: return@preserveOriginalOnFailure

                val isImeSupport = callIsImeSupport(
                    stub = stub,
                    context = inputMethodService.applicationContext,
                ) ?: return@preserveOriginalOnFailure

                if (!isImeSupport) {
                    internationalBuildField.setBoolean(thisObject, true)
                }
            }

            chain.proceed()
        }
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
        private const val TARGET_CLASS_NAME = "android.inputmethodservice.InputMethodService"
    }
}
