package io.github.togls.hypertweaks.core.xposed

interface HookFeature {
    val id: String
    val preferenceKey: String
    val targets: Set<HookTarget>

    fun supports(environment: HookEnvironment): Boolean {
        return targets.any { target -> target.matches(environment) }
    }

    fun install(context: HookFeatureContext): HookInstallResult
}

interface HookFeatureProvider {
    fun features(): List<HookFeature>
}

sealed interface HookInstallResult {
    data class Installed(
        val installedTargets: Set<String> = emptySet(),
        val failedTargets: Set<String> = emptySet(),
    ) : HookInstallResult

    data class Unsupported(
        val reason: String,
    ) : HookInstallResult

    data class Failed(
        val error: Throwable,
    ) : HookInstallResult
}

sealed interface ResolveResult<out T> {
    data class Resolved<T>(
        val value: T,
    ) : ResolveResult<T>

    data class NotFound(
        val reason: String,
    ) : ResolveResult<Nothing>

    data class Failed(
        val error: Throwable,
    ) : ResolveResult<Nothing>
}
