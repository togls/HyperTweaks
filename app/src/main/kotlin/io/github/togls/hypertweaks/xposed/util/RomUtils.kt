package io.github.togls.hypertweaks.xposed.util

import android.os.Build

object RomUtils {
    fun isXiaomiLikeRom(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val display = Build.DISPLAY.orEmpty().lowercase()
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()

        return manufacturer in XIAOMI_MANUFACTURERS ||
            brand in XIAOMI_MANUFACTURERS ||
            display.contains("hyperos") ||
            display.contains("miui") ||
            fingerprint.contains("hyperos") ||
            fingerprint.contains("miui")
    }

    private val XIAOMI_MANUFACTURERS = setOf(
        "xiaomi",
        "redmi",
        "poco",
    )
}