package io.github.togls.hypertweaks.core.xposed

fun Throwable.rethrowIfFatal() {
    if (this is VirtualMachineError || this is ThreadDeath) {
        throw this
    }
}
