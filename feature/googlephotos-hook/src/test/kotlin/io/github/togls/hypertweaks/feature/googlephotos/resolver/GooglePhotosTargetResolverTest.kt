package io.github.togls.hypertweaks.feature.googlephotos.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GooglePhotosTargetResolverTest {
    @Test
    fun exactClassNameIsTheFastPath() {
        val diagnostics = mutableListOf<ResolveDiagnostic>()
        val resolver = resolver(diagnostics)

        val resolved = resolver.resolve(GooglePhotosTarget.ACTIVITY)

        assertEquals(ResolveStage.EXACT, resolved.source)
        assertEquals("android.app.Activity", resolved.className)
        assertTrue(diagnostics.any { it.stage == ResolveStage.EXACT })
        assertTrue(diagnostics.none { it.stage == ResolveStage.VERSION_MAPPING })
    }

    @Test
    fun mapViewUsesExplicitMapsInternalClassLoaderCandidates() {
        val loaderSet = GooglePhotosClassLoaderSet(
            applicationClassLoader = javaClass.classLoader!!,
            threadContextClassLoader = FakeContextClassLoader(javaClass.classLoader!!),
        )

        val sources = loaderSet.candidates(GooglePhotosClassLoaderRole.MAPS_INTERNAL)
            .map(ClassLoaderCandidate::source)

        assertTrue(sources.first().startsWith("thread_context"))
        assertTrue(sources.any { source -> source.startsWith("application") })
    }

    @Test
    fun knownVersionMappingFallsBackAfterFixedNameFailure() {
        val diagnostics = mutableListOf<ResolveDiagnostic>()
        val profile = GooglePhotosKnownTargetProfile(
            versionName = "test",
            classNames = mapOf(
                GooglePhotosTarget.CAMERA_UPDATE_FACTORY to FallbackFactory::class.java.name,
            ),
        )
        val resolver = GooglePhotosTargetResolver(
            applicationClassLoader = javaClass.classLoader!!,
            diagnostics = ResolveDiagnostics { diagnostic -> diagnostics += diagnostic },
            knownProfiles = listOf(profile),
            threadContextClassLoader = javaClass.classLoader,
        )

        val resolved = resolver.resolve(GooglePhotosTarget.CAMERA_UPDATE_FACTORY)

        assertEquals(ResolveStage.VERSION_MAPPING, resolved.source)
        assertEquals(FallbackFactory::class.java, resolved.targetClass)
        assertTrue(diagnostics.any { it.stage == ResolveStage.STRUCTURAL })
    }

    @Test
    fun failedFixedTargetEmitsStructuralCandidateDiagnostics() {
        val diagnostics = mutableListOf<ResolveDiagnostic>()
        val resolver = resolver(diagnostics)

        try {
            resolver.resolve(GooglePhotosTarget.MAP_VIEW)
            fail("Expected unsupported target")
        } catch (error: UnsupportedGooglePhotosTargetException) {
            assertEquals(GooglePhotosTarget.MAP_VIEW, error.target)
        }

        assertTrue(
            diagnostics.any { diagnostic ->
                diagnostic.target == "map_view" &&
                    diagnostic.stage == ResolveStage.STRUCTURAL &&
                    diagnostic.outcome == ResolveOutcome.REJECTED
            },
        )
        assertTrue(diagnostics.any { it.stage == ResolveStage.FAILED })
    }

    private fun resolver(diagnostics: MutableList<ResolveDiagnostic>): GooglePhotosTargetResolver {
        return GooglePhotosTargetResolver(
            applicationClassLoader = javaClass.classLoader!!,
            diagnostics = ResolveDiagnostics { diagnostic -> diagnostics += diagnostic },
            threadContextClassLoader = javaClass.classLoader,
        )
    }

    private class FakeContextClassLoader(parent: ClassLoader) : ClassLoader(parent)
    private class FallbackFactory
}
