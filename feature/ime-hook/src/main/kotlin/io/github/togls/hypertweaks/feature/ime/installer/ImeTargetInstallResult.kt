package io.github.togls.hypertweaks.feature.ime.installer

import io.github.togls.hypertweaks.core.xposed.HookFeatureContext
import io.github.togls.hypertweaks.core.xposed.HookInstallResult

internal sealed interface ImeTargetInstallResult {
    val target: String

    data class Installed(
        override val target: String,
    ) : ImeTargetInstallResult

    data class Skipped(
        override val target: String,
        val reason: String,
    ) : ImeTargetInstallResult

    data class Failed(
        override val target: String,
        val error: Throwable,
    ) : ImeTargetInstallResult
}

internal data class ImeTargetInstaller(
    val target: String,
    val install: (HookFeatureContext) -> List<ImeTargetInstallResult>,
)

internal class ImeInstallCoordinator {
    fun install(
        context: HookFeatureContext,
        installers: List<ImeTargetInstaller>,
    ): HookInstallResult.Installed {
        val results = installers.flatMap { installer ->
            installSafely(context, installer)
        }
        return HookInstallResult.Installed(
            installedTargets = results.filterIsInstance<ImeTargetInstallResult.Installed>()
                .mapTo(mutableSetOf(), ImeTargetInstallResult.Installed::target),
            failedTargets = results.filterIsInstance<ImeTargetInstallResult.Failed>()
                .mapTo(mutableSetOf(), ImeTargetInstallResult.Failed::target),
        )
    }

    private fun installSafely(
        context: HookFeatureContext,
        installer: ImeTargetInstaller,
    ): List<ImeTargetInstallResult> {
        val results = try {
            installer.install(context)
        } catch (error: Throwable) {
            rethrowFatal(error)
            listOf(ImeTargetInstallResult.Failed(installer.target, error))
        }
        results.forEach { result -> logResult(context, result) }
        return results
    }

    private fun logResult(
        context: HookFeatureContext,
        result: ImeTargetInstallResult,
    ) {
        when (result) {
            is ImeTargetInstallResult.Installed -> context.log.info(
                event = "hook.target.installed",
                fields = resultFields(result.target, "target_installed"),
            )

            is ImeTargetInstallResult.Skipped -> context.log.info(
                event = "hook.target.skipped",
                message = result.reason,
                fields = resultFields(result.target, result.reason),
            )

            is ImeTargetInstallResult.Failed -> context.log.error(
                event = "hook.install.failed",
                throwable = result.error,
                fields = resultFields(result.target, "installer_exception"),
            )
        }
    }

    private fun resultFields(target: String, reason: String): Map<String, String> {
        return mapOf("subtarget" to target, "reason" to reason)
    }

    private fun rethrowFatal(error: Throwable) {
        if (error is VirtualMachineError || error is ThreadDeath) {
            throw error
        }
    }
}
