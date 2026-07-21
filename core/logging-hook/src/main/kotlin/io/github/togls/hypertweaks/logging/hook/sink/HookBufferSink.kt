package io.github.togls.hypertweaks.logging.hook.sink

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.hook.HookEventBuffer

class HookBufferSink(
    private val buffer: HookEventBuffer,
) : LogSink {
    override fun emit(event: LogEvent) {
        buffer.offer(event)
    }
}
