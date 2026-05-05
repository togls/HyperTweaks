package io.github.togls.miaospime.xposed.hook

import android.content.Context
import android.inputmethodservice.InputMethodService
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.miaospime.xposed.util.HookLog
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class InputMethodServiceHook(
    private val module: XposedModule,
) {

    fun install(classLoader: ClassLoader) {
        val inputMethodServiceClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            HookLog.w(module, "skip InputMethodServiceHook: class not found", error)
        }.getOrNull() ?: return

        val internationalBuildField = runCatching {
            inputMethodServiceClass.getDeclaredField("IS_INTERNATIONAL_BUILD").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(module, "skip InputMethodServiceHook: IS_INTERNATIONAL_BUILD not found", error)
        }.getOrNull() ?: return

        val hideImeRenderMethod = runCatching {
            inputMethodServiceClass.getDeclaredMethod(
                "hideImeRenderGesturalNavButtons",
                String::class.java,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(
                module,
                "skip InputMethodServiceHook: hideImeRenderGesturalNavButtons(String) not found",
                error,
            )
        }.getOrNull() ?: return

        module.hook(hideImeRenderMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                runCatching {
                    val thisObject = chain.getThisObject() ?: return@runCatching
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
                    HookLog.e(module, "hook hideImeRenderGesturalNavButtons failed", error)
                }

                chain.proceed()
            }

        HookLog.i(module, "hooked InputMethodService#hideImeRenderGesturalNavButtons(String)")
    }

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