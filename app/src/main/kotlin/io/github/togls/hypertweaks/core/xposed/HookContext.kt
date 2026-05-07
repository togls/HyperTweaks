package io.github.togls.hypertweaks.core.xposed

import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.core.xposed.util.HookLog

class HookContext(
    val module: XposedModule,
    val log: HookLog,
) {

    fun child(component: String): HookContext {
        return HookContext(
            module = module,
            log = log.child(component),
        )
    }
}