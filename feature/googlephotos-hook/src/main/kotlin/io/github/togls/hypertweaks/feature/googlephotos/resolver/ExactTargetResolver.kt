package io.github.togls.hypertweaks.feature.googlephotos.resolver

internal data class ClassLoaderCandidate(
    val source: String,
    val classLoader: ClassLoader,
)

internal data class ClassResolutionCandidate(
    val targetClass: Class<*>,
    val className: String,
    val classLoaderSource: String,
    val source: ResolveStage,
)

internal class ExactTargetResolver(
    private val diagnostics: ResolveDiagnostics,
) {
    fun resolve(
        target: GooglePhotosTarget,
        classLoaders: List<ClassLoaderCandidate>,
    ): ClassResolutionCandidate? {
        classLoaders.forEach { candidate ->
            val resolved = load(target, target.exactClassName, candidate, ResolveStage.EXACT)
            if (resolved != null) return resolved
        }
        return null
    }

    internal fun load(
        target: GooglePhotosTarget,
        className: String,
        candidate: ClassLoaderCandidate,
        stage: ResolveStage,
    ): ClassResolutionCandidate? {
        diagnostics.record(
            target,
            stage,
            ResolveOutcome.ATTEMPTED,
            className,
            candidate.source,
        )
        val targetClass = loadClass(target, className, candidate, stage) ?: return null
        if (!target.validator(targetClass)) {
            diagnostics.record(
                target,
                stage,
                ResolveOutcome.REJECTED,
                className,
                candidate.source,
                "structural validation failed",
            )
            return null
        }
        return ClassResolutionCandidate(targetClass, className, candidate.source, stage)
    }

    private fun loadClass(
        target: GooglePhotosTarget,
        className: String,
        candidate: ClassLoaderCandidate,
        stage: ResolveStage,
    ): Class<*>? {
        return try {
            candidate.classLoader.loadClass(className)
        } catch (error: ClassNotFoundException) {
            recordLoadFailure(target, className, candidate, stage, error)
            null
        } catch (error: LinkageError) {
            recordLoadFailure(target, className, candidate, stage, error)
            null
        }
    }

    private fun recordLoadFailure(
        target: GooglePhotosTarget,
        className: String,
        candidate: ClassLoaderCandidate,
        stage: ResolveStage,
        error: Throwable,
    ) {
        diagnostics.record(
            target,
            stage,
            ResolveOutcome.REJECTED,
            className,
            candidate.source,
            "${error.javaClass.simpleName}: ${error.message}",
        )
    }
}
