package io.github.togls.hypertweaks.feature.googlephotos.resolver

internal enum class ResolveStage {
    STARTED,
    EXACT,
    VERSION_MAPPING,
    STRUCTURAL,
    COMPLETED,
    FAILED,
}

internal enum class ResolveOutcome {
    ATTEMPTED,
    SELECTED,
    REJECTED,
}

internal data class ResolveDiagnostic(
    val target: String,
    val stage: ResolveStage,
    val outcome: ResolveOutcome,
    val className: String? = null,
    val classLoaderSource: String? = null,
    val detail: String? = null,
)

internal fun interface ResolveDiagnosticSink {
    fun record(diagnostic: ResolveDiagnostic)
}

internal class ResolveDiagnostics(
    private val sink: ResolveDiagnosticSink,
) {
    fun record(
        target: GooglePhotosTarget,
        stage: ResolveStage,
        outcome: ResolveOutcome,
        className: String? = null,
        classLoaderSource: String? = null,
        detail: String? = null,
    ) {
        sink.record(
            ResolveDiagnostic(
                target = target.logName,
                stage = stage,
                outcome = outcome,
                className = className,
                classLoaderSource = classLoaderSource,
                detail = detail,
            ),
        )
    }
}
