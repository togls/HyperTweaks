package io.github.togls.hypertweaks.core.xposed

fun aggregateHookInstallResult(
    installedTargets: Set<String>,
    failedTargets: Map<String, Throwable>,
    unsupportedReason: String,
    hasUsableInstalledTarget: Boolean = installedTargets.isNotEmpty(),
): HookInstallResult {
    require(!hasUsableInstalledTarget || installedTargets.isNotEmpty()) {
        "A usable Hook target must also be included in installedTargets"
    }
    failedTargets.values.forEach(Throwable::rethrowIfFatal)
    if (hasUsableInstalledTarget) {
        return HookInstallResult.Installed(
            installedTargets = installedTargets,
            failedTargets = failedTargets.keys,
        )
    }
    if (failedTargets.isNotEmpty()) {
        return HookInstallResult.Failed(aggregateFailure(failedTargets))
    }
    return HookInstallResult.Unsupported(unsupportedReason)
}

private fun aggregateFailure(failures: Map<String, Throwable>): Throwable {
    val orderedFailures = failures.entries.sortedBy(Map.Entry<String, Throwable>::key)
    val primaryFailure = orderedFailures.first()
    return IllegalStateException(
        "Hook target installation failed: ${orderedFailures.joinToString { it.key }}",
        primaryFailure.value,
    ).apply {
        orderedFailures.drop(1).forEach { (_, error) -> addSuppressed(error) }
    }
}
