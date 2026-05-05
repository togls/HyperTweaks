package io.github.togls.hypertweaks.xposed.hook

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.xposed.util.HookLog

class DeadZoneHook(
    private val module: XposedModule,
) {

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            HookLog.w(module, "skip DeadZoneHook: class not found", error)
        }.getOrNull() ?: return

        val sizeMinField = runCatching {
            targetClass.getDeclaredField("mSizeMin").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(module, "skip DeadZoneHook: mSizeMin not found", error)
        }.getOrNull() ?: return

        val onConfigurationChangedMethod = runCatching {
            targetClass.getDeclaredMethod(
                "onConfigurationChanged",
                Int::class.javaPrimitiveType,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(
                module,
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
                    HookLog.e(module, "hook DeadZone.onConfigurationChanged failed", error)
                }

                result
            }

        HookLog.i(module, "hooked DeadZone#onConfigurationChanged(int)")
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.DeadZone"
    }
}