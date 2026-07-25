package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstaller
import io.github.togls.hypertweaks.logging.api.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMethodPackageStrategyTest {
    @Test
    fun `matched input method package is logged and installed`() {
        val events = mutableListOf<LogEvent>()
        val strategy = InputMethodPackageStrategy(
            installers = listOf(installed("navigation_bar")),
        )
        val context = imeTestContext(
            sdkInt = 35,
            packageName = SupportedPackage,
            isSystemServer = false,
            events = events,
        )

        val result = strategy.install(context)

        assertEquals(setOf("navigation_bar"), (result as HookInstallResult.Installed).installedTargets)
        val matchEvent = events.single { event -> event.event == "ime.package.match" }
        assertEquals(SupportedPackage, matchEvent.fields["package_name"])
        assertEquals("true", matchEvent.fields["matched"])
    }

    @Test
    fun `unmatched package is logged and returns Unsupported`() {
        val events = mutableListOf<LogEvent>()
        val strategy = InputMethodPackageStrategy(installers = emptyList())
        val context = imeTestContext(
            sdkInt = 35,
            packageName = "org.example.not.ime",
            isSystemServer = false,
            events = events,
        )

        val result = strategy.install(context)

        assertTrue(result is HookInstallResult.Unsupported)
        val matchEvent = events.single { event -> event.event == "ime.package.match" }
        assertEquals("false", matchEvent.fields["matched"])
    }

    @Test
    fun `matched package on API 33 returns Unsupported without installing`() {
        var installCalls = 0
        val strategy = InputMethodPackageStrategy(
            installers = listOf(
                ImeTargetInstaller("unexpected") {
                    installCalls += 1
                    listOf(ImeTargetInstallResult.Installed("unexpected"))
                },
            ),
        )

        val result = strategy.install(
            imeTestContext(
                sdkInt = 33,
                packageName = SupportedPackage,
                isSystemServer = false,
            ),
        )

        assertTrue(result is HookInstallResult.Unsupported)
        assertEquals(0, installCalls)
    }

    private fun installed(target: String): ImeTargetInstaller {
        return ImeTargetInstaller(target) {
            listOf(ImeTargetInstallResult.Installed(target))
        }
    }

    private companion object {
        private const val SupportedPackage = "com.google.android.inputmethod.latin"
    }
}
