package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.view.RoundedCorner
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.core.xposed.util.HookLog
import io.github.togls.hypertweaks.core.xposed.util.dpToPx
import java.util.WeakHashMap

class NavigationBarViewHook(
    private val module: XposedModule,
) {

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            HookLog.w(module, "skip NavigationBarViewHook: class not found", error)
        }.getOrNull() ?: return

        val updateOrientationViewsMethod = runCatching {
            targetClass.getDeclaredMethod("updateOrientationViews").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(
                module,
                "skip NavigationBarViewHook: updateOrientationViews not found",
                error,
            )
        }.getOrNull() ?: return

        val horizontalField = runCatching {
            targetClass.getDeclaredField("mHorizontal").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            HookLog.w(module, "skip NavigationBarViewHook: mHorizontal not found", error)
        }.getOrNull() ?: return

        module.hook(updateOrientationViewsMethod)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val thisObject = chain.thisObject ?: return@intercept result

                runCatching {
                    val horizontalView = horizontalField.get(thisObject) as? View
                        ?: return@runCatching

                    installRoundedCornerPaddingListener(horizontalView)
                }.onFailure { error ->
                    HookLog.e(module, "hook NavigationBarView.updateOrientationViews failed", error)
                }

                result
            }

        HookLog.i(module, "hooked NavigationBarView#updateOrientationViews")
    }

    private fun installRoundedCornerPaddingListener(horizontalView: View) {
        val shadow = dpToPx(4, horizontalView.resources)

        horizontalView.setOnApplyWindowInsetsListener { view, insets ->
            val basePadding = basePaddings.getOrPut(view) {
                intArrayOf(
                    view.paddingLeft + shadow,
                    view.paddingTop,
                    view.paddingRight + shadow,
                    view.paddingBottom,
                )
            }

            val bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
            val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)

            val bottomLeftRadius = bottomLeft?.radius ?: 0
            val bottomRightRadius = bottomRight?.radius ?: 0

            view.setPadding(
                calculateRoundedCornerPadding(
                    cornerRadius = bottomLeftRadius,
                    basePadding = basePadding[0],
                ),
                basePadding[1],
                calculateRoundedCornerPadding(
                    cornerRadius = bottomRightRadius,
                    basePadding = basePadding[2],
                ),
                basePadding[3],
            )

            insets
        }
    }

    private fun calculateRoundedCornerPadding(
        cornerRadius: Int,
        basePadding: Int,
    ): Int {
        if (cornerRadius <= 0) {
            return basePadding
        }

        return maxOf(0, cornerRadius - basePadding)
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarView"

        private val basePaddings = WeakHashMap<View, IntArray>()
    }
}