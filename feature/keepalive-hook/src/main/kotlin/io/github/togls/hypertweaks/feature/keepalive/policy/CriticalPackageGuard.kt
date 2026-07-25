package io.github.togls.hypertweaks.feature.keepalive.policy

import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import java.util.Locale

object CriticalPackageGuard {
    private val criticalPackageNames = setOf(
        "android",
        "com.android.phone",
        "com.android.providers.settings",
        "com.android.settings",
        "com.android.systemui",
    )

    private val criticalProcessNames = setOf(
        "android",
        "init",
        "servicemanager",
        "surfaceflinger",
        "system",
        "system_server",
        "zygote",
        "zygote64",
    )

    fun isCritical(value: String?): Boolean {
        val trimmedValue = value?.trim()?.lowercase(Locale.ROOT) ?: return false
        val baseName = trimmedValue.substringBefore(':')
        val normalizedPackage = KeepAlivePackages.normalizeCandidate(baseName)
        return baseName in criticalProcessNames ||
            normalizedPackage in criticalPackageNames
    }
}
