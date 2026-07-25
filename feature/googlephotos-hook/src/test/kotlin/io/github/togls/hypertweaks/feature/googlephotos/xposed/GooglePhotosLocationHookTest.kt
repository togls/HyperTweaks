package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookEngine
import io.github.togls.hypertweaks.core.xposed.HookEnvironment
import io.github.togls.hypertweaks.core.xposed.HookHandle
import io.github.togls.hypertweaks.core.xposed.HookInstallGuard
import io.github.togls.hypertweaks.core.xposed.HookInstallKey
import io.github.togls.hypertweaks.core.xposed.HookInstallRecord
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.HookInstallState
import io.github.togls.hypertweaks.core.xposed.HookInterceptor
import io.github.togls.hypertweaks.core.xposed.HookSettingsProvider
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookSettingsState
import io.github.togls.hypertweaks.core.xposed.HookSettingsSubscription
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallCoordinator
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallStep
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosInstallTarget
import io.github.togls.hypertweaks.feature.googlephotos.logging.GooglePhotosDiagnosticsPolicy
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosClassNames
import io.github.togls.hypertweaks.logging.api.NoOpLogger
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosLocationHookTest {
    @Test
    fun unsupportedInstallReleasesStateForRetry() {
        var attemptCount = 0
        val hook = hook {
            attemptCount += 1
            if (attemptCount == 1) {
                coordinator().install(
                    GooglePhotosHookInstallStep(
                        target = GooglePhotosInstallTarget.MARKER_API,
                        enabled = false,
                        disabledReason = "TARGET_UNAVAILABLE",
                    ) {},
                )
            } else {
                successfulStrategyResult()
            }
        }

        val first = hook.install(testClassLoader())
        val second = hook.install(testClassLoader())

        assertTrue(first is HookInstallResult.Unsupported)
        assertTrue(second is HookInstallResult.Installed)
        assertEquals(2, attemptCount)
    }

    @Test
    fun failedInstallReleasesStateForRetry() {
        var attemptCount = 0
        val hook = hook {
            attemptCount += 1
            if (attemptCount == 1) {
                coordinator().install(
                    GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MARKER_API) {
                        error("first attempt failed")
                    },
                )
            } else {
                successfulStrategyResult()
            }
        }

        val first = hook.install(testClassLoader())
        val second = hook.install(testClassLoader())

        assertTrue(first is HookInstallResult.Failed)
        assertTrue(second is HookInstallResult.Installed)
        assertEquals(2, attemptCount)
    }

    private fun hook(
        installer: (ClassLoader) ->
            io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallResult,
    ): GooglePhotosLocationHook {
        return GooglePhotosLocationHook(
            context = testContext(),
            diagnosticsPolicy = GooglePhotosDiagnosticsPolicy.forBuild(debug = false),
            processNameProvider = { GooglePhotosClassNames.PackageName },
            installHooksOverride = installer,
        )
    }

    private fun successfulStrategyResult() = coordinator().install(
        GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MARKER_API) {},
    )

    private fun coordinator() = GooglePhotosHookInstallCoordinator()

    private fun testContext(): HookContext {
        return HookContext(
            environment = HookEnvironment(
                packageName = GooglePhotosClassNames.PackageName,
                processName = GooglePhotosClassNames.PackageName,
                classLoader = testClassLoader(),
                sdkInt = 36,
                sessionId = "googlephotos-test",
                isSystemServer = false,
            ),
            engine = TestEngine,
            settings = HookSettingsSnapshot.Disabled,
            logger = NoOpLogger,
            installGuard = TestInstallGuard,
            settingsProvider = TestSettingsProvider,
        )
    }

    private fun testClassLoader(): ClassLoader = checkNotNull(javaClass.classLoader)
}

private object TestEngine : HookEngine {
    override fun hook(executable: Executable, interceptor: HookInterceptor): HookHandle {
        error("Hook engine must not be called by state tests")
    }

    override fun deoptimize(executable: Executable): Boolean = false
}

private object TestInstallGuard : HookInstallGuard {
    override fun tryStart(key: HookInstallKey): Boolean = true
    override fun markInstalled(key: HookInstallKey) = Unit
    override fun markDeferred(key: HookInstallKey) = Unit
    override fun markFailed(
        key: HookInstallKey,
        retryable: Boolean,
        failureStage: String,
        failureMessage: String?,
    ): Long? = null
    override fun state(key: HookInstallKey): HookInstallState = HookInstallState.NEW
    override fun record(key: HookInstallKey): HookInstallRecord = HookInstallRecord()
}

private object TestSettingsProvider : HookSettingsProvider {
    override val currentState: HookSettingsState =
        HookSettingsState.Ready(HookSettingsSnapshot.Disabled)

    override fun subscribe(
        listener: (HookSettingsState) -> Unit,
    ): HookSettingsSubscription = HookSettingsSubscription {}
}
