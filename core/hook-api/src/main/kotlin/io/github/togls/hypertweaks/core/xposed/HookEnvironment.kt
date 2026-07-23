package io.github.togls.hypertweaks.core.xposed

data class HookEnvironment(
    val packageName: String,
    val processName: String,
    val classLoader: ClassLoader,
    val sdkInt: Int,
    val sessionId: String,
    val isSystemServer: Boolean = false,
    val targetVersionCode: Long? = null,
)

sealed interface HookTarget {
    val id: String

    fun matches(environment: HookEnvironment): Boolean

    data object SystemServer : HookTarget {
        override val id: String = "system_server"

        override fun matches(environment: HookEnvironment): Boolean = environment.isSystemServer
    }

    data class Packages(
        val packageNames: Set<String>,
    ) : HookTarget {
        init {
            require(packageNames.isNotEmpty()) { "Package target must not be empty" }
        }

        override val id: String = packageNames.sorted().joinToString(separator = ",")

        override fun matches(environment: HookEnvironment): Boolean {
            return !environment.isSystemServer && environment.packageName in packageNames
        }
    }
}
