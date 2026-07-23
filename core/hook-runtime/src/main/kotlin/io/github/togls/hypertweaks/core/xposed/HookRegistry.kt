package io.github.togls.hypertweaks.core.xposed

class HookRegistry(
    private val dispatcher: HookDispatcher,
) {
    fun install(environment: HookEnvironment): List<HookFeatureDispatchResult> {
        return dispatcher.dispatch(environment)
    }
}
