package io.github.togls.hypertweaks.feature.keepalive.policy

import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import java.util.concurrent.atomic.AtomicReference

class KeepAlivePolicy(
    initialSettings: HookSettingsSnapshot,
) {
    private val configuration = AtomicReference(Configuration.from(initialSettings))

    fun update(settings: HookSettingsSnapshot): Configuration {
        val nextConfiguration = Configuration.from(settings)
        configuration.set(nextConfiguration)
        return nextConfiguration
    }

    fun currentConfiguration(): Configuration = configuration.get()

    fun shouldInstallProcessKillHooks(): Boolean {
        val current = configuration.get()
        return current.packages.isNotEmpty() && current.mode != KeepAliveMode.OomOnly
    }

    fun shouldInstallOomAdjHooks(): Boolean = configuration.get().packages.isNotEmpty()

    fun decideProcessKill(
        packageName: String,
        group: ProcessKillGroup,
    ): Decision {
        val matchedPackage = matchConfiguredPackage(packageName)
            ?: return Decision.Allow(reason = "package_not_configured")
        val mode = configuration.get().mode
        return when (mode) {
            KeepAliveMode.OomOnly -> Decision.Allow(matchedPackage, "oom_only_preserves_kill")
            KeepAliveMode.Audit -> Decision.Audit(matchedPackage, "audit_mode")
            KeepAliveMode.Conservative -> decideConservative(matchedPackage, group)
            KeepAliveMode.Aggressive -> Decision.Block(matchedPackage, "full_mode_explicit_package")
        }
    }

    fun decideOomAdj(
        packageName: String,
        requestedAdj: Int,
        protectedAdj: Int,
    ): Decision {
        val matchedPackage = matchConfiguredPackage(packageName)
            ?: return Decision.Allow(reason = "package_not_configured")
        if (requestedAdj <= protectedAdj) {
            return Decision.Allow(matchedPackage, "requested_adj_already_protected")
        }
        return if (configuration.get().mode == KeepAliveMode.Audit) {
            Decision.Audit(matchedPackage, "audit_mode")
        } else {
            Decision.Clamp(matchedPackage, "oom_adj_above_protected_threshold")
        }
    }

    private fun decideConservative(
        packageName: String,
        group: ProcessKillGroup,
    ): Decision {
        return if (group.conservativeProtection) {
            Decision.Block(packageName, "conservative_cleanup_group")
        } else {
            Decision.Allow(packageName, "conservative_preserves_explicit_kill")
        }
    }

    private fun matchConfiguredPackage(candidate: String): String? {
        if (CriticalPackageGuard.isCritical(candidate)) return null
        val normalized = KeepAlivePackages.normalizeCandidate(candidate) ?: return null
        return normalized.takeIf { packageName -> packageName in configuration.get().packages }
    }

    data class Configuration(
        val mode: KeepAliveMode,
        val packages: Set<String>,
    ) {
        companion object {
            fun from(settings: HookSettingsSnapshot): Configuration {
                return Configuration(
                    mode = KeepAliveMode.fromValue(settings.keepAliveMode),
                    packages = KeepAlivePackages.parse(settings.keepAlivePackages),
                )
            }
        }
    }
}

enum class ProcessKillGroup(
    val persistedName: String,
    val conservativeProtection: Boolean,
) {
    AmsBackground("AMS_BACKGROUND", true),
    AmsAggressive("AMS_AGGRESSIVE", false),
    ProcessListCleanup("PROCESS_LIST_CLEANUP", true),
    ProcessListRemove("PROCESS_LIST_REMOVE", false),
    ProcessRecordKill("PROCESS_RECORD_KILL", false),
    MiuiProcessManager("MIUI_PROCESS_MANAGER_SERVICE", true),
    MiuiSmartPower("MIUI_SMART_POWER", true),
}

sealed interface Decision {
    val packageName: String?
    val reason: String

    data class Allow(
        override val packageName: String? = null,
        override val reason: String,
    ) : Decision

    data class Audit(
        override val packageName: String,
        override val reason: String,
    ) : Decision

    data class Block(
        override val packageName: String,
        override val reason: String,
    ) : Decision

    data class Clamp(
        override val packageName: String,
        override val reason: String,
    ) : Decision
}
