package io.github.togls.hypertweaks.core.xposed

fun Throwable.rethrowIfFatal() {
    if (this is Error) throw this
}
