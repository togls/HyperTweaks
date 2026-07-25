package io.github.togls.hypertweaks.feature.keepalive.xposed

import java.lang.reflect.Method

class OomAdjResolver {
    fun resolve(classLoader: ClassLoader): OomAdjResolution {
        val failures = mutableMapOf<String, Throwable>()
        val processRecordClass = loadClass(classLoader, ProcessRecordClass, failures)
        val processListClass = loadClass(classLoader, ProcessListClass, failures)
        return OomAdjResolution(
            setPidMethods = processRecordClass?.declaredMethods
                ?.filter { method -> method.isSupportedSetPid() }
                .orEmpty(),
            setOomAdjMethods = processListClass?.declaredMethods
                ?.filter { method -> method.isSupportedSetOomAdj() }
                .orEmpty(),
            failures = failures,
        )
    }

    private fun loadClass(
        classLoader: ClassLoader,
        className: String,
        failures: MutableMap<String, Throwable>,
    ): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (_: ClassNotFoundException) {
            null
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            failures[className] = error
            null
        }
    }

    companion object {
        internal fun Method.isSupportedSetPid(): Boolean {
            return name == "setPid" &&
                parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }

        internal fun Method.isSupportedSetOomAdj(): Boolean {
            val intType = Int::class.javaPrimitiveType
            return name == "setOomAdj" &&
                parameterTypes.contentEquals(arrayOf(intType, intType, intType))
        }

        private const val ProcessRecordClass = "com.android.server.am.ProcessRecord"
        private const val ProcessListClass = "com.android.server.am.ProcessList"
    }
}

data class OomAdjResolution(
    val setPidMethods: List<Method>,
    val setOomAdjMethods: List<Method>,
    val failures: Map<String, Throwable>,
)
