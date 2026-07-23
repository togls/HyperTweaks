package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext

class DeadZoneHook(
    context: HookContext,
) {
    private val engine = context.engine
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

        engine.hook(onConfigurationChangedMethod) { chain ->
                val result = chain.proceed()
                val thisObject = chain.thisObject ?: return@hook result

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
