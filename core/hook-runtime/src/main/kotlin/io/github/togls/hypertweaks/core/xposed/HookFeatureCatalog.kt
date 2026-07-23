package io.github.togls.hypertweaks.core.xposed

class HookFeatureCatalog(
    providers: List<HookFeatureProvider>,
) {
    val features: List<HookFeature> = providers.flatMap(HookFeatureProvider::features)

    init {
        val duplicateIds = features.groupingBy(HookFeature::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Hook feature ids must be unique: ${duplicateIds.sorted().joinToString()}"
        }
    }

    fun matching(environment: HookEnvironment): List<HookFeature> {
        return features.filter { feature -> feature.supports(environment) }
    }

    fun scopeEntries(): Set<String> {
        return features.flatMap { feature ->
            feature.targets.flatMap { target ->
                when (target) {
                    HookTarget.SystemServer -> listOf("system")
                    is HookTarget.Packages -> target.packageNames
                }
            }
        }.toSet()
    }
}
