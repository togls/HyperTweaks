package io.github.togls.hypertweaks.core.xposed

import java.lang.reflect.Executable

interface HookEngine {
    fun hook(
        executable: Executable,
        interceptor: HookInterceptor,
    ): HookHandle

    fun deoptimize(executable: Executable): Boolean
}

fun interface HookInterceptor {
    fun intercept(chain: HookChain): Any?
}

interface HookChain {
    val executable: Executable
    val thisObject: Any?
    val args: List<Any?>

    fun getArg(index: Int): Any? = args[index]

    fun proceed(): Any?

    fun proceed(arguments: Array<out Any?>): Any?

    fun proceedWith(thisObject: Any): Any?

    fun proceedWith(
        thisObject: Any,
        arguments: Array<out Any?>,
    ): Any?
}

interface HookHandle {
    val executable: Executable

    fun unhook()
}
