package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext

class DeadZoneHook(
    context: HookContext,
) {
    private val module = context.module
    private val log = context.log

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip DeadZoneHook: class not found", error)
        }.getOrNull() ?: return

        val sizeMinField = runCatching {
            targetClass.getDeclaredField("mSizeMin").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip DeadZoneHook: mSizeMin not found", error)
        }.getOrNull() ?: return

        val onConfigurationChangedMethod = runCatching {
            targetClass.getDeclaredMethod(
                "onConfigurationChanged",
                Int::class.javaPrimitiveType,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip DeadZoneHook: onConfigurationChanged(int) not found",
                error,
            )
        }.getOrNull() ?: return

        module.hook(onConfigurationChangedMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val thisObject = chain.thisObject ?: return@intercept result

                runCatching {
                    sizeMinField.setInt(thisObject, 0)
                }.onFailure { error ->
                    log.e("hook DeadZone.onConfigurationChanged failed", error)
                }

                result
            }

        log.i("hooked DeadZone#onConfigurationChanged(int)")
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.DeadZone"
    }
}