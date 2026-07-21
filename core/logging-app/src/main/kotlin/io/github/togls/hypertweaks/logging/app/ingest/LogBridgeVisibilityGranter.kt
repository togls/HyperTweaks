package io.github.togls.hypertweaks.logging.app.ingest

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.togls.hypertweaks.logging.api.LogProtocol

internal object LogBridgeVisibilityGranter {
    fun grant(context: Context): VisibilityGrantSummary {
        val ingestUri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(LogProtocol.Authority)
            .build()
        return grantInstalledPackages(
            packages = CallerValidator.DefaultAllowedPackages - context.packageName,
            isInstalled = { packageName -> context.isPackageInstalled(packageName) },
            grant = { packageName ->
                context.grantUriPermission(
                    packageName,
                    ingestUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            },
        )
    }

    private fun Context.isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

internal fun grantInstalledPackages(
    packages: Set<String>,
    isInstalled: (String) -> Boolean,
    grant: (String) -> Unit,
): VisibilityGrantSummary {
    val grantedPackages = mutableSetOf<String>()
    val failures = mutableMapOf<String, String>()
    var installedPackageCount = 0
    packages.forEach { packageName ->
        val installed = try {
            isInstalled(packageName)
        } catch (error: Exception) {
                failures[packageName] = error.javaClass.name
                false
        }
        if (!installed) return@forEach
        installedPackageCount++
        try {
            grant(packageName)
            grantedPackages += packageName
        } catch (error: Exception) {
            failures[packageName] = error.javaClass.name
        }
    }
    return VisibilityGrantSummary(
        requestedPackageCount = packages.size,
        installedPackageCount = installedPackageCount,
        grantedPackages = grantedPackages.toSet(),
        failures = failures.toMap(),
    )
}

internal data class VisibilityGrantSummary(
    val requestedPackageCount: Int,
    val installedPackageCount: Int,
    val grantedPackages: Set<String>,
    val failures: Map<String, String>,
)
