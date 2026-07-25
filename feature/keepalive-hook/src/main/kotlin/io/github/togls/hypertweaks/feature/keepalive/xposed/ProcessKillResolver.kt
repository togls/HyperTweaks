package io.github.togls.hypertweaks.feature.keepalive.xposed

import android.content.pm.ApplicationInfo
import io.github.togls.hypertweaks.feature.keepalive.policy.ProcessKillGroup
import java.lang.reflect.Method

class ProcessKillResolver {
    fun resolve(classLoader: ClassLoader): ProcessKillResolution {
        val targets = mutableListOf<ProcessKillTarget>()
        val unavailableClasses = mutableSetOf<String>()
        val failures = mutableMapOf<String, Throwable>()
        classSpecs.forEach { classSpec ->
            resolveClass(classLoader, classSpec, targets, unavailableClasses, failures)
        }
        return ProcessKillResolution(targets, unavailableClasses, failures)
    }

    private fun resolveClass(
        classLoader: ClassLoader,
        classSpec: ProcessKillClassSpec,
        targets: MutableList<ProcessKillTarget>,
        unavailableClasses: MutableSet<String>,
        failures: MutableMap<String, Throwable>,
    ) {
        val targetClass = try {
            classLoader.loadClass(classSpec.className)
        } catch (_: ClassNotFoundException) {
            unavailableClasses += classSpec.className
            return
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            failures[classSpec.className] = error
            return
        }
        try {
            targets += classSpec.resolve(targetClass)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            failures[classSpec.className] = error
        }
    }

    companion object {
        private val AmsBackgroundMethods = setOf(
            "killBackgroundProcesses",
            "killBackgroundProcessesWithFeature",
            "killPackageDependents",
        )
        private val AmsAggressiveMethods = setOf(
            "forceStopPackage",
            "forceStopPackageAsUser",
            "forceStopPackageLocked",
        )
        private val ProcessListKeywords = setOf("kill", "remove")
        private val MiuiProcessManagerKeywords = setOf(
            "kill",
            "clean",
            "trim",
            "forceStop",
            "remove",
        )
        private val MiuiSmartPowerKeywords = setOf(
            "kill",
            "clean",
            "trim",
            "hibernate",
            "idle",
            "power",
            "freeze",
        )

        private val classSpecs = listOf(
            ProcessKillClassSpec("com.android.server.am.ActivityManagerService", ::resolveAms),
            ProcessKillClassSpec("com.android.server.am.ProcessList", ::resolveProcessList),
            ProcessKillClassSpec("com.android.server.am.ProcessRecord", ::resolveProcessRecord),
            ProcessKillClassSpec(
                "com.miui.server.process.ProcessManagerService",
                ::resolveMiuiProcessManager,
            ),
            ProcessKillClassSpec(
                "com.miui.server.smartpower.SmartPowerService",
                ::resolveMiuiSmartPower,
            ),
        )

        internal fun resolveAms(targetClass: Class<*>): List<ProcessKillTarget> {
            return targetClass.declaredMethods.mapNotNull { method ->
                val group = when (method.name) {
                    in AmsBackgroundMethods -> ProcessKillGroup.AmsBackground
                    in AmsAggressiveMethods -> ProcessKillGroup.AmsAggressive
                    else -> null
                }
                group?.takeIf { method.hasSupportedPackageParameter() }?.let {
                    ProcessKillTarget(method, it, packageFromReceiver = false)
                }
            }
        }

        internal fun resolveProcessList(targetClass: Class<*>): List<ProcessKillTarget> {
            return targetClass.declaredMethods.mapNotNull { method ->
                if (!method.matchesAny(ProcessListKeywords) || !method.hasSupportedPackageParameter()) {
                    return@mapNotNull null
                }
                val group = if (method.name.contains("remove", ignoreCase = true)) {
                    ProcessKillGroup.ProcessListRemove
                } else {
                    ProcessKillGroup.ProcessListCleanup
                }
                ProcessKillTarget(method, group, packageFromReceiver = false)
            }
        }

        internal fun resolveProcessRecord(targetClass: Class<*>): List<ProcessKillTarget> {
            return targetClass.declaredMethods
                .filter { method -> method.isSupportedProcessRecordKill() }
                .map { method ->
                    ProcessKillTarget(
                        method = method,
                        group = ProcessKillGroup.ProcessRecordKill,
                        packageFromReceiver = true,
                    )
                }
        }

        internal fun resolveMiuiProcessManager(targetClass: Class<*>): List<ProcessKillTarget> {
            return resolveKeywordMethods(
                targetClass,
                MiuiProcessManagerKeywords,
                ProcessKillGroup.MiuiProcessManager,
            )
        }

        internal fun resolveMiuiSmartPower(targetClass: Class<*>): List<ProcessKillTarget> {
            return resolveKeywordMethods(
                targetClass,
                MiuiSmartPowerKeywords,
                ProcessKillGroup.MiuiSmartPower,
            )
        }

        private fun resolveKeywordMethods(
            targetClass: Class<*>,
            keywords: Set<String>,
            group: ProcessKillGroup,
        ): List<ProcessKillTarget> {
            return targetClass.declaredMethods
                .filter { method ->
                    method.matchesAny(keywords) && method.hasSupportedPackageParameter()
                }
                .map { method -> ProcessKillTarget(method, group, packageFromReceiver = false) }
        }

        private fun Method.matchesAny(keywords: Set<String>): Boolean {
            return keywords.any { keyword -> name.contains(keyword, ignoreCase = true) }
        }

        private fun Method.hasSupportedPackageParameter(): Boolean {
            return parameterTypes.any { parameterType ->
                parameterType == String::class.java ||
                    parameterType.isArray ||
                    Iterable::class.java.isAssignableFrom(parameterType) ||
                    Map::class.java.isAssignableFrom(parameterType) ||
                    ApplicationInfo::class.java.isAssignableFrom(parameterType)
            }
        }

        private fun Method.isSupportedProcessRecordKill(): Boolean {
            val firstParameter = parameterTypes.firstOrNull()
            return (name == "kill" || name == "killLocked") &&
                returnType == Void.TYPE &&
                firstParameter == String::class.java
        }
    }
}

data class ProcessKillTarget(
    val method: Method,
    val group: ProcessKillGroup,
    val packageFromReceiver: Boolean,
)

data class ProcessKillResolution(
    val targets: List<ProcessKillTarget>,
    val unavailableClasses: Set<String>,
    val failures: Map<String, Throwable>,
)

private data class ProcessKillClassSpec(
    val className: String,
    val resolve: (Class<*>) -> List<ProcessKillTarget>,
)
