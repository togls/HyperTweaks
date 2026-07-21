package io.github.togls.hypertweaks.logging.hook.sink

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.hook.HookBatchDispatcher

class HookLogBridgeSink(
    private val dispatcher: HookBatchDispatcher,
) : LogSink {
    override fun emit(event: LogEvent) {
        dispatcher.enqueue(event)
    }
}
