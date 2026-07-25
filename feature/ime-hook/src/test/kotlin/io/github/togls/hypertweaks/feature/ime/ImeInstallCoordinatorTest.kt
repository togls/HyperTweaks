package io.github.togls.hypertweaks.feature.ime

import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeInstallCoordinator
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeInstallCoordinatorTest {
    @Test
    fun `navigation bar dead zone and bottom manager install independently`() {
        val attemptedTargets = mutableListOf<String>()
        val installers = listOf(
            successfulInstaller("navigation_bar", attemptedTargets),
            throwingInstaller("dead_zone", attemptedTargets),
            successfulInstaller("bottom_manager", attemptedTargets),
        )

        val result = ImeInstallCoordinator().install(imeTestContext(35), installers)
            as HookInstallResult.Installed

        assertEquals(listOf("navigation_bar", "dead_zone", "bottom_manager"), attemptedTargets)
        assertEquals(setOf("navigation_bar", "bottom_manager"), result.installedTargets)
        assertEquals(setOf("dead_zone"), result.failedTargets)
    }

    @Test
    fun `missing optional MIUI target is skipped without failing other targets`() {
        val installers = listOf(
            ImeTargetInstaller("input_method_service") {
                listOf(ImeTargetInstallResult.Installed("input_method_service"))
            },
            ImeTargetInstaller("bottom_manager") {
                listOf(
                    ImeTargetInstallResult.Skipped(
                        target = "bottom_manager.load_dex",
                        reason = "MIUI class not found",
                    ),
                )
            },
            ImeTargetInstaller("dead_zone") {
                listOf(ImeTargetInstallResult.Installed("dead_zone"))
            },
        )

        val result = ImeInstallCoordinator().install(imeTestContext(35), installers)
            as HookInstallResult.Installed

        assertEquals(setOf("input_method_service", "dead_zone"), result.installedTargets)
        assertTrue(result.failedTargets.isEmpty())
    }

    @Test
    fun `multiple method results remain independently visible`() {
        val installer = ImeTargetInstaller("navigation_bar_controller") {
            listOf(
                ImeTargetInstallResult.Installed("controller.caption_height"),
                ImeTargetInstallResult.Failed(
                    "controller.ime_switch_click",
                    IllegalStateException("missing method"),
                ),
            )
        }

        val result = ImeInstallCoordinator().install(imeTestContext(36), listOf(installer))
            as HookInstallResult.Installed

        assertEquals(setOf("controller.caption_height"), result.installedTargets)
        assertEquals(setOf("controller.ime_switch_click"), result.failedTargets)
    }

    @Test
    fun `all failed targets return Failed`() {
        val installers = listOf(
            ImeTargetInstaller("navigation_bar") {
                listOf(
                    ImeTargetInstallResult.Failed(
                        "navigation_bar",
                        IllegalStateException("navigation failed"),
                    ),
                )
            },
            ImeTargetInstaller("dead_zone") {
                error("dead zone failed")
            },
        )

        val result = ImeInstallCoordinator().install(imeTestContext(36), installers)

        assertTrue(result is HookInstallResult.Failed)
        val error = (result as HookInstallResult.Failed).error
        assertTrue(error.message.orEmpty().contains("dead_zone"))
        assertTrue(error.message.orEmpty().contains("navigation_bar"))
        assertEquals("dead zone failed", error.cause?.message)
        assertEquals(1, error.suppressed.size)
    }

    @Test
    fun `all skipped targets return Unsupported`() {
        val installers = listOf(
            skippedInstaller("bottom_manager", "MIUI class not found"),
            skippedInstaller("navigation_bar", "method not found"),
        )

        val result = ImeInstallCoordinator().install(imeTestContext(36), installers)

        assertTrue(result is HookInstallResult.Unsupported)
        val reason = (result as HookInstallResult.Unsupported).reason
        assertTrue(reason.contains("bottom_manager (MIUI class not found)"))
        assertTrue(reason.contains("navigation_bar (method not found)"))
    }

    private fun successfulInstaller(
        target: String,
        attemptedTargets: MutableList<String>,
    ): ImeTargetInstaller {
        return ImeTargetInstaller(target) {
            attemptedTargets += target
            listOf(ImeTargetInstallResult.Installed(target))
        }
    }

    private fun throwingInstaller(
        target: String,
        attemptedTargets: MutableList<String>,
    ): ImeTargetInstaller {
        return ImeTargetInstaller(target) {
            attemptedTargets += target
            error("install failed")
        }
    }

    private fun skippedInstaller(target: String, reason: String): ImeTargetInstaller {
        return ImeTargetInstaller(target) {
            listOf(ImeTargetInstallResult.Skipped(target, reason))
        }
    }
}
