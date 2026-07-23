package io.github.togls.hypertweaks.core.xposed

import java.util.concurrent.ConcurrentHashMap

class ProcessHookInstallGuard : HookInstallGuard {
    private val states = ConcurrentHashMap<HookInstallKey, HookInstallState>()

    override fun tryStart(key: HookInstallKey): Boolean {
        return states.putIfAbsent(key, HookInstallState.INSTALLING) == null
    }

    override fun markInstalled(key: HookInstallKey) {
        states.computeIfPresent(key) { _, state ->
            require(state == HookInstallState.INSTALLING) {
                "Cannot mark hook installed from state $state"
            }
            HookInstallState.INSTALLED
        }
    }

    override fun markFailed(key: HookInstallKey) {
        states.computeIfPresent(key) { _, state ->
            require(state == HookInstallState.INSTALLING) {
                "Cannot mark hook failed from state $state"
            }
            HookInstallState.FAILED
        }
    }

    override fun state(key: HookInstallKey): HookInstallState {
        return states[key] ?: HookInstallState.NEW
    }
}
