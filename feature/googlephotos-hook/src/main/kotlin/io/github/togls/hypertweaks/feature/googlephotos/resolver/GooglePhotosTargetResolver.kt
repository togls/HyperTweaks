package io.github.togls.hypertweaks.feature.googlephotos.resolver

internal data class ResolvedGooglePhotosTarget(
    val target: GooglePhotosTarget,
    val targetClass: Class<*>,
    val className: String,
    val classLoaderSource: String,
    val source: ResolveStage,
)

internal class GooglePhotosTargetResolver(
    applicationClassLoader: ClassLoader,
    private val diagnostics: ResolveDiagnostics,
    knownProfiles: List<GooglePhotosKnownTargetProfile> =
        listOf(GooglePhotosKnownTargetProfiles.Photos783),
    threadContextClassLoader: ClassLoader? = Thread.currentThread().contextClassLoader,
) {
    private val classLoaders = GooglePhotosClassLoaderSet(
        applicationClassLoader,
        threadContextClassLoader,
    )
    private val exactResolver = ExactTargetResolver(diagnostics)
    private val structuralResolver =
        StructuralTargetResolver(exactResolver, diagnostics, knownProfiles)

    fun resolve(target: GooglePhotosTarget): ResolvedGooglePhotosTarget {
        diagnostics.record(target, ResolveStage.STARTED, ResolveOutcome.ATTEMPTED)
        val candidates = classLoaders.candidates(target.classLoaderRole)
        val resolved = exactResolver.resolve(target, candidates)
            ?: structuralResolver.resolve(target, candidates)
            ?: resolutionFailed(target, candidates)
        diagnostics.record(
            target,
            ResolveStage.COMPLETED,
            ResolveOutcome.SELECTED,
            resolved.className,
            resolved.classLoaderSource,
            "source=${resolved.source}",
        )
        return ResolvedGooglePhotosTarget(
            target,
            resolved.targetClass,
            resolved.className,
            resolved.classLoaderSource,
            resolved.source,
        )
    }

    fun bindingSelected(target: GooglePhotosTarget, detail: String) {
        diagnostics.record(
            target,
            ResolveStage.STRUCTURAL,
            ResolveOutcome.SELECTED,
            detail = detail,
        )
    }

    private fun resolutionFailed(
        target: GooglePhotosTarget,
        candidates: List<ClassLoaderCandidate>,
    ): Nothing {
        val sources = candidates.joinToString(",") { candidate -> candidate.source }
        diagnostics.record(
            target,
            ResolveStage.FAILED,
            ResolveOutcome.REJECTED,
            target.exactClassName,
            detail = "classLoaderCandidates=$sources",
        )
        error("Unable to resolve ${target.logName}: ${target.exactClassName}")
    }
}

internal class GooglePhotosClassLoaderSet(
    applicationClassLoader: ClassLoader,
    threadContextClassLoader: ClassLoader?,
) {
    private val application = hierarchy("application", applicationClassLoader)
    private val mapsInternal = (
        threadContextClassLoader?.let { hierarchy("thread_context", it) }.orEmpty() + application
        )

    fun candidates(role: GooglePhotosClassLoaderRole): List<ClassLoaderCandidate> {
        return when (role) {
            GooglePhotosClassLoaderRole.APPLICATION -> application
            GooglePhotosClassLoaderRole.MAPS_INTERNAL -> mapsInternal
            GooglePhotosClassLoaderRole.PLATFORM -> application
        }
    }

    private fun hierarchy(prefix: String, first: ClassLoader): List<ClassLoaderCandidate> {
        val candidates = mutableListOf<ClassLoaderCandidate>()
        var current: ClassLoader? = first
        var depth = 0
        while (current != null) {
            candidates += ClassLoaderCandidate("$prefix[$depth]", current)
            current = current.parent
            depth += 1
        }
        return candidates
    }
}
