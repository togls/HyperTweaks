package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class InputMethodServiceHook(
    context: HookContext
) {

    private val module = context.module
    private val log = context.log

    fun install(classLoader: ClassLoader) {
        val inputMethodServiceClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip InputMethodServiceHook: class not found", error)
        }.getOrNull() ?: return

        val internationalBuildField = runCatching {
            inputMethodServiceClass.getDeclaredField("IS_INTERNATIONAL_BUILD").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodServiceHook: IS_INTERNATIONAL_BUILD not found",
                error
            )
        }.getOrNull() ?: return

        val hideImeRenderMethod = runCatching {
            inputMethodServiceClass.getDeclaredMethod(
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
        }.getOrNull() ?: return

        module.hook(hideImeRenderMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                runCatching {
                    val thisObject = chain.thisObject ?: return@runCatching
                    val inputMethodService = thisObject as? InputMethodService ?: return@runCatching

                    val stub = loadInputMethodServiceStub(
                        classLoader = classLoader,
                        receiver = thisObject,
                    ) ?: return@runCatching

                    val isImeSupport = callIsImeSupport(
                        stub = stub,
                        context = inputMethodService.applicationContext,
                    ) ?: return@runCatching

                    if (!isImeSupport) {
                        internationalBuildField.setBoolean(thisObject, true)
                    }
                }.onFailure { error ->
                    log.e("hook hideImeRenderGesturalNavButtons failed", error)
                }

                chain.proceed()
            }

        log.i("hooked InputMethodService#hideImeRenderGesturalNavButtons(String)")
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

    private companion object {
        private const val TARGET_CLASS_NAME = "android.inputmethodservice.InputMethodService"
    }
}