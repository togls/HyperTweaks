package io.github.togls.hypertweaks.core.xposed

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun interface HookRetryScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit)
}

internal class ProcessHookRetryScheduler : HookRetryScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "HyperTweaks-HookRetry").apply {
            isDaemon = true
        }
    }

    override fun schedule(delayMillis: Long, action: () -> Unit) {
        executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
    }
}
