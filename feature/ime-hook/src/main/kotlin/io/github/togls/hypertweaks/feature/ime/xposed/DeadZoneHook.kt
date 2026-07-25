package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult

class DeadZoneHook(
    context: HookContext,
) {
    private val engine = context.engine
    private val log = context.log

    @SuppressLint("PrivateApi")
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip DeadZoneHook: class not found", error)
        }.getOrNull() ?: return skipped("DeadZone class not found")

        val sizeMinField = runCatching {
            targetClass.getDeclaredField("mSizeMin").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip DeadZoneHook: mSizeMin not found", error)
        }.getOrNull() ?: return skipped("DeadZone.mSizeMin not found")

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
        }.getOrNull() ?: return skipped("DeadZone.onConfigurationChanged(int) not found")

        val installResult = installImeTarget(Target, log) {
            engine.hook(onConfigurationChangedMethod) { chain ->
                val originalResult = chain.proceed()
                val thisObject = chain.thisObject ?: return@hook originalResult

                preserveOriginalOnFailure(log, "DeadZone.onConfigurationChanged", Unit) {
                    sizeMinField.setInt(thisObject, 0)
                }

                originalResult
            }
            log.i("hooked DeadZone#onConfigurationChanged(int)")
        }
        return listOf(installResult)
    }

    private fun skipped(reason: String): List<ImeTargetInstallResult> {
        return listOf(skipImeTarget(Target, reason, log))
    }

    private companion object {
        private const val Target = "dead_zone.on_configuration_changed"
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.DeadZone"
    }
}
