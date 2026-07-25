package io.github.togls.hypertweaks.feature.ime

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

        assertEquals(setOf("controller.caption_height"), result.installedTargets)
        assertEquals(setOf("controller.ime_switch_click"), result.failedTargets)
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
}
