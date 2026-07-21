package io.github.togls.hypertweaks.core.xposed

import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.Logger

class HookContext(
    val module: XposedModule,
    val log: Logger,
) {

    fun child(component: String): HookContext {
        return HookContext(
            module = module,
            log = log.child(component),
        )
    }

    fun withTarget(
        packageName: String,
        processName: String,
        sessionId: String,
    ): HookContext {
        return HookContext(
            module = module,
            log = log.withContext(
                LogContext(
                    packageName = packageName,
                    processName = processName,
                    sessionId = sessionId,
                ),
            ),
        )
    }
}
