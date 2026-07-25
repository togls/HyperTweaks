package io.github.togls.hypertweaks.feature.googlephotos.install

internal enum class GooglePhotosInstallTarget(
    val logName: String,
    val isStrategy: Boolean,
) {
    LIFECYCLE("lifecycle", false),
    MAP_VIEW("map_view", false),
    MARKER_API("marker_api", true),
    MARKER_ANIMATION("marker_animation", true),
    INITIAL_PREVIEW_SELECTION("initial_preview_selection", true),
    MAP_LOCATION("map_location", true),
    CAMERA_UPDATE("camera_update", true),
    HEATMAP_INDEX("heatmap_index", true),
    S2_QUERY("s2_query", true),
}

internal data class GooglePhotosHookInstallStep(
    val target: GooglePhotosInstallTarget,
    val enabled: Boolean = true,
    val install: () -> Unit,
)

internal data class GooglePhotosHookInstallResult(
    private val outcomes: Map<GooglePhotosInstallTarget, Boolean>,
) {
    fun installed(target: GooglePhotosInstallTarget): Boolean = outcomes[target] == true
}

internal class GooglePhotosHookInstallCoordinator(
    private val onBegin: (GooglePhotosInstallTarget) -> Unit = {},
    private val onSuccess: (GooglePhotosInstallTarget) -> Unit = {},
    private val onSkipped: (GooglePhotosInstallTarget) -> Unit = {},
    private val onFailure: (GooglePhotosInstallTarget, Throwable) -> Unit = { _, _ -> },
) {
    fun install(vararg steps: GooglePhotosHookInstallStep): GooglePhotosHookInstallResult {
        val outcomes = linkedMapOf<GooglePhotosInstallTarget, Boolean>()
        steps.forEach { step -> outcomes[step.target] = installStep(step) }
        return GooglePhotosHookInstallResult(outcomes)
    }

    private fun installStep(step: GooglePhotosHookInstallStep): Boolean {
        if (!step.enabled) {
            onSkipped(step.target)
            return false
        }
        onBegin(step.target)
        return try {
            step.install()
            onSuccess(step.target)
            true
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            onFailure(step.target, error)
            false
        }
    }
}
