package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookEngine
import io.github.togls.hypertweaks.core.xposed.HookHandle
import io.github.togls.hypertweaks.core.xposed.HookInterceptor
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.Logger
import java.lang.reflect.Executable
import java.lang.reflect.Method

internal class RecordingHookEngine(
    private val failingExecutables: Set<Executable> = emptySet(),
) : HookEngine {
    val interceptors = mutableMapOf<Executable, HookInterceptor>()

    override fun hook(executable: Executable, interceptor: HookInterceptor): HookHandle {
        check(executable !in failingExecutables) { "simulated hook installation failure" }
        interceptors[executable] = interceptor
        return object : HookHandle {
            override val executable: Executable = executable

            override fun unhook() {
                interceptors.remove(executable)
            }
        }
    }

    override fun deoptimize(executable: Executable): Boolean = false

    fun invoke(method: Method, chain: HookChain): Any? {
        return requireNotNull(interceptors[method]).intercept(chain)
    }
}

internal open class RecordingHookChain(
    override val executable: Executable,
    override val thisObject: Any?,
    override val args: List<Any?>,
    private val originalResult: Any?,
) : HookChain {
    var proceedCount: Int = 0
    var replacementArguments: Array<out Any?>? = null

    override fun proceed(): Any? {
        proceedCount += 1
        return originalResult
    }

    override fun proceed(arguments: Array<out Any?>): Any? {
        proceedCount += 1
        replacementArguments = arguments
        return originalResult
    }

    override fun proceedWith(thisObject: Any): Any? = proceed()

    override fun proceedWith(thisObject: Any, arguments: Array<out Any?>): Any? {
        replacementArguments = arguments
        return proceed()
    }
}

internal class RecordingLogger : Logger {
    val events = mutableListOf<RecordedLogEvent>()

    override fun child(tag: String): Logger = this
    override fun withField(key: String, value: Any?): Logger = this
    override fun withFields(fields: Map<String, String>): Logger = this
    override fun withContext(context: LogContext): Logger = this

    override fun debug(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) {
        events += RecordedLogEvent(event, fields, throwable)
    }

    override fun info(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) {
        events += RecordedLogEvent(event, fields, throwable)
    }

    override fun warn(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) {
        events += RecordedLogEvent(event, fields, throwable)
    }

    override fun error(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) {
        events += RecordedLogEvent(event, fields, throwable)
    }
}

internal data class RecordedLogEvent(
    val event: String,
    val fields: Map<String, String>,
    val throwable: Throwable?,
)
