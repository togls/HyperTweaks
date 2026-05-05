package io.github.togls.miaospime.xposed.hook

import android.inputmethodservice.InputMethodService
import android.os.Build
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.miaospime.xposed.util.HookLog
import io.github.togls.miaospime.xposed.util.dpToPx
import java.lang.reflect.Method

class NavigationBarControllerHook(
    private val module: XposedModule,
) {

    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            HookLog.w(module, "skip NavigationBarControllerHook: class not found", error)
        }.getOrNull() ?: return

        val imeDrawsImeNavBarField = runCatching {
            targetClass.getDeclaredField("mImeDrawsImeNavBar").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(module, "skip NavigationBarControllerHook: mImeDrawsImeNavBar not found", error)
        }.getOrNull() ?: return

        val serviceField = runCatching {
            targetClass.getDeclaredField("mService").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(module, "skip NavigationBarControllerHook: mService not found", error)
        }.getOrNull() ?: return

        val captionBarHeightMethod = findCaptionBarHeightMethod(targetClass)
            ?: run {
                HookLog.w(module, "skip NavigationBarControllerHook: getImeCaptionBarHeight method not found")
                return
            }

        module.hook(captionBarHeightMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                runCatching {
                    val thisObject = chain.getThisObject() ?: return@runCatching null

                    val args = chain.getArgs()
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
                    HookLog.e(module, "hook getImeCaptionBarHeight failed", error)
                }.getOrNull()
                    ?: chain.proceed()
            }

        HookLog.i(module, "hooked NavigationBarController\$Impl#getImeCaptionBarHeight")
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

    private companion object {
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.NavigationBarController\$Impl"
    }
}