package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeVersionStrategyTest {
    @Test
    fun `API 34 and 35 use Android 34 to 35 strategy`() {
        val strategy = Android34To35Strategy(installers = listOf(installed("legacy")))

        assertTrue(strategy.supports(34))
        assertTrue(strategy.supports(35))
        assertFalse(strategy.supports(33))
        assertFalse(strategy.supports(36))
        val result = strategy.install(imeTestContext(34)) as HookInstallResult.Installed
        assertEquals(
            setOf("legacy"),
            result.installedTargets,
        )
    }

    @Test
    fun `API 36 and newer use Android 36 plus strategy`() {
        val strategy = Android36PlusStrategy(installers = listOf(installed("modern")))

        assertFalse(strategy.supports(35))
        assertTrue(strategy.supports(36))
        assertTrue(strategy.supports(40))
        val result = strategy.install(imeTestContext(36)) as HookInstallResult.Installed
        assertEquals(
            setOf("modern"),
            result.installedTargets,
        )
    }

    @Test
    fun `unsupported system server version returns Unsupported`() {
        val feature = ImeSystemServerFeature(
            strategies = listOf(
                Android34To35Strategy(installers = listOf(installed("legacy"))),
                Android36PlusStrategy(installers = listOf(installed("modern"))),
            ),
        )

        val result = feature.install(imeTestContext(33))

        assertTrue(result is HookInstallResult.Unsupported)
        assertTrue((result as HookInstallResult.Unsupported).reason.contains("API 34+"))
    }

    @Test
    fun `feature provider separates system server and package targets`() {
        val features = ImeHookFeatureProvider().features().associateBy { feature -> feature.id }
        val systemContext = imeTestContext(35)
        val packageContext = imeTestContext(
            sdkInt = 35,
            packageName = "com.google.android.inputmethod.latin",
            isSystemServer = false,
        )

        assertTrue(features.getValue("ime.system-server").supports(systemContext.environment))
        assertFalse(features.getValue("ime.system-server").supports(packageContext.environment))
        assertFalse(features.getValue("ime.package").supports(systemContext.environment))
        assertTrue(features.getValue("ime.package").supports(packageContext.environment))
    }

    private fun installed(target: String): ImeTargetInstaller {
        return ImeTargetInstaller(target) {
            listOf(ImeTargetInstallResult.Installed(target))
        }
    }
}
