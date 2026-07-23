package io.github.togls.hypertweaks.core.xposed

import io.github.togls.hypertweaks.logging.api.NoOpLogger
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookDispatcherTest {
    @Test
    fun featureFailureDoesNotStopLaterFeature() {
        val installed = mutableListOf<String>()
        val features = listOf(
            TestFeature("broken") { error("install failed") },
            TestFeature("healthy") {
                installed += "healthy"
                HookInstallResult.Installed()
            },
        )
        val dispatcher = dispatcher(features)

        val results = dispatcher.dispatch(environment())

        assertTrue(results[0] is HookFeatureDispatchResult.Failed)
        assertTrue(results[1] is HookFeatureDispatchResult.Installed)
        assertEquals(listOf("healthy"), installed)
    }

    @Test
    fun sameFeatureIsInstalledOnlyOncePerProcessTarget() {
        var installCount = 0
        val dispatcher = dispatcher(
            listOf(
                TestFeature("once") {
                    installCount++
                    HookInstallResult.Installed()
                },
            ),
        )
        val environment = environment()

        dispatcher.dispatch(environment)
        val secondResults = dispatcher.dispatch(environment)

        assertEquals(1, installCount)
        assertTrue(secondResults.single() is HookFeatureDispatchResult.Duplicate)
    }

    @Test
    fun unavailableSettingsDisableAllFeatures() {
        val feature = TestFeature("never") { error("must not run") }
        val settingsProvider = TestSettingsProvider(
            HookSettingsState.Unavailable(IllegalStateException("offline")),
        )
        val dispatcher = HookDispatcher(
            catalog = HookFeatureCatalog(listOf(TestProvider(listOf(feature)))),
            engine = TestEngine,
            settingsProvider = settingsProvider,
            installGuard = ProcessHookInstallGuard(),
            logger = NoOpLogger,
        )

        assertTrue(dispatcher.dispatch(environment()).isEmpty())
    }

    private fun dispatcher(features: List<HookFeature>): HookDispatcher {
        return HookDispatcher(
            catalog = HookFeatureCatalog(listOf(TestProvider(features))),
            engine = TestEngine,
            settingsProvider = TestSettingsProvider(
                HookSettingsState.Ready(
                    HookSettingsSnapshot(enabledPreferenceKeys = setOf(PreferenceKey)),
                ),
            ),
            installGuard = ProcessHookInstallGuard(),
            logger = NoOpLogger,
        )
    }

    private fun environment(): HookEnvironment {
        return HookEnvironment(
            packageName = "system_server",
            processName = "system_server",
            classLoader = checkNotNull(javaClass.classLoader),
            sdkInt = 36,
            sessionId = "session",
            isSystemServer = true,
        )
    }

    private class TestFeature(
        override val id: String,
        private val installer: () -> HookInstallResult,
    ) : HookFeature {
        override val preferenceKey: String = PreferenceKey
        override val targets: Set<HookTarget> = setOf(HookTarget.SystemServer)

        override fun install(context: HookFeatureContext): HookInstallResult = installer()
    }

    private class TestProvider(
        private val values: List<HookFeature>,
    ) : HookFeatureProvider {
        override fun features(): List<HookFeature> = values
    }

    private class TestSettingsProvider(
        override val currentState: HookSettingsState,
    ) : HookSettingsProvider {
        override fun subscribe(
            listener: (HookSettingsState) -> Unit,
        ): HookSettingsSubscription = HookSettingsSubscription {}
    }

    private object TestEngine : HookEngine {
        override fun hook(
            executable: Executable,
            interceptor: HookInterceptor,
        ): HookHandle = error("unused")

        override fun deoptimize(executable: Executable): Boolean = false
    }

    private companion object {
        const val PreferenceKey = "test_enabled"
    }
}
