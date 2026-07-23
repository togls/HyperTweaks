package io.github.togls.hypertweaks.core.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

class LibXposedHookEngine(
    private val module: XposedModule,
) : HookEngine {
    override fun hook(
        executable: Executable,
        interceptor: HookInterceptor,
    ): HookHandle {
        val delegate = module.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                interceptor.intercept(LibXposedHookChain(chain))
            }
        return LibXposedHookHandle(delegate)
    }

    override fun deoptimize(executable: Executable): Boolean = module.deoptimize(executable)
}

private class LibXposedHookChain(
    private val delegate: XposedInterface.Chain,
) : HookChain {
    override val executable: Executable
        get() = delegate.executable

    override val thisObject: Any?
        get() = delegate.thisObject

    override val args: List<Any?>
        get() = delegate.args

    override fun proceed(): Any? = delegate.proceed()

    override fun proceed(arguments: Array<out Any?>): Any? {
        return delegate.proceed(arguments.copyOf())
    }

    override fun proceedWith(thisObject: Any): Any? = delegate.proceedWith(thisObject)

    override fun proceedWith(
        thisObject: Any,
        arguments: Array<out Any?>,
    ): Any? {
        return delegate.proceedWith(thisObject, arguments.copyOf())
    }
}

private class LibXposedHookHandle(
    private val delegate: XposedInterface.HookHandle,
) : HookHandle {
    override val executable: Executable
        get() = delegate.executable

    override fun unhook() {
        delegate.unhook()
    }
}
