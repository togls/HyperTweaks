package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.view.RoundedCorner
import android.view.View
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.util.dpToPx
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.util.WeakHashMap

class NavigationBarViewHook(
    context: HookContext
) {

    private val engine = context.engine
    private val log = context.log

    @SuppressLint("PrivateApi")
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip NavigationBarViewHook: class not found", error)
        }.getOrNull() ?: return skipped("NavigationBarView class not found")

        val updateOrientationViewsMethod = runCatching {
            targetClass.getDeclaredMethod("updateOrientationViews").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip NavigationBarViewHook: updateOrientationViews not found", error)
        }.getOrNull() ?: return skipped("NavigationBarView.updateOrientationViews not found")

        val horizontalField = runCatching {
            targetClass.getDeclaredField("mHorizontal").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip NavigationBarViewHook: mHorizontal not found", error)
        }.getOrNull() ?: return skipped("NavigationBarView.mHorizontal not found")

        val installResult = installImeTarget(Target, log) {
            engine.hook(updateOrientationViewsMethod) { chain ->
                val originalResult = chain.proceed()
                val thisObject = chain.thisObject ?: return@hook originalResult

                preserveOriginalOnFailure(log, "NavigationBarView.updateOrientationViews", Unit) {
                    val horizontalView = horizontalField.get(thisObject) as? View
                        ?: return@preserveOriginalOnFailure

                    installRoundedCornerPaddingListener(horizontalView)
                }

                originalResult
            }
            log.i("hooked NavigationBarView#updateOrientationViews")
        }
        return listOf(installResult)
    }

    private fun installRoundedCornerPaddingListener(horizontalView: View) {
        val shadow = dpToPx(4, horizontalView.resources)

        horizontalView.setOnApplyWindowInsetsListener { view, insets ->
            preserveOriginalOnFailure(log, "NavigationBarView.onApplyWindowInsets", insets) {
                val basePadding = basePaddings.getOrPut(view) {
                    intArrayOf(
                        view.paddingLeft + shadow,
                        view.paddingTop,
                        view.paddingRight + shadow,
                        view.paddingBottom,
                    )
                }
                applyRoundedCornerPadding(view, insets, basePadding)
                insets
            }
        }
    }

    private fun applyRoundedCornerPadding(
        view: View,
        insets: android.view.WindowInsets,
        basePadding: IntArray,
    ) {
        val bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
        val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)

        val bottomLeftRadius = bottomLeft?.radius ?: 0
        val bottomRightRadius = bottomRight?.radius ?: 0

        view.setPadding(
            calculateRoundedCornerPadding(bottomLeftRadius, basePadding[0]),
            basePadding[1],
            calculateRoundedCornerPadding(bottomRightRadius, basePadding[2]),
            basePadding[3],
        )
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

    private fun skipped(reason: String): List<ImeTargetInstallResult> {
        return listOf(skipImeTarget(Target, reason, log))
    }

    private companion object {
        private const val Target = "navigation_bar_view.update_orientation_views"
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.navigationbar.NavigationBarView"

        private val basePaddings = WeakHashMap<View, IntArray>()
    }
}
