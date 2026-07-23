package io.github.togls.hypertweaks.core.xposed.util

import android.content.res.Resources
import android.util.TypedValue

fun dpToPx(
    value: Int,
    resources: Resources,
): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()
}
