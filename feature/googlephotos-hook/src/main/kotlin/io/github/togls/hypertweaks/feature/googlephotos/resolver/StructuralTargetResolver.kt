package io.github.togls.hypertweaks.feature.googlephotos.resolver

internal class StructuralTargetResolver(
    private val exactResolver: ExactTargetResolver,
    private val diagnostics: ResolveDiagnostics,
    private val knownProfiles: List<GooglePhotosKnownTargetProfile>,
) {
    fun resolve(
        target: GooglePhotosTarget,
        classLoaders: List<ClassLoaderCandidate>,
    ): ClassResolutionCandidate? {
        val candidates = knownProfiles.mapNotNull { profile ->
            profile.classNames[target]?.let { className -> profile.versionName to className }
        }.distinctBy { (_, className) -> className }
            .filterNot { (_, className) -> className == target.exactClassName }
        val matches = candidates.flatMap { (versionName, className) ->
            resolveCandidate(target, className, versionName, classLoaders)
        }.distinctBy { candidate -> candidate.targetClass }
        return matches.singleOrNull().also { match ->
            if (matches.size > 1) {
                diagnostics.record(
                    target,
                    ResolveStage.STRUCTURAL,
                    ResolveOutcome.REJECTED,
                    detail = "ambiguous candidates=${matches.size}",
                )
            } else if (match == null && candidates.isEmpty()) {
                diagnostics.record(
                    target,
                    ResolveStage.STRUCTURAL,
                    ResolveOutcome.REJECTED,
                    detail = "no known structural candidates",
                )
            }
        }
    }

    private fun resolveCandidate(
        target: GooglePhotosTarget,
        className: String,
        versionName: String,
        classLoaders: List<ClassLoaderCandidate>,
    ): List<ClassResolutionCandidate> {
        return classLoaders.mapNotNull { classLoader ->
            exactResolver.load(
                target,
                className,
                classLoader,
                ResolveStage.VERSION_MAPPING,
            )?.also {
                diagnostics.record(
                    target,
                    ResolveStage.STRUCTURAL,
                    ResolveOutcome.SELECTED,
                    className,
                    classLoader.source,
                    "profile=$versionName",
                )
            }
        }
    }
}
