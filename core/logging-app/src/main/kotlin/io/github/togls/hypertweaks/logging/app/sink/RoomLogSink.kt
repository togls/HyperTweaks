package io.github.togls.hypertweaks.logging.app.sink

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogLimits
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.app.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class RoomLogSink(
    scope: CoroutineScope,
    private val repository: LogRepository,
    private val onWriteFailure: (List<LogEvent>, Throwable) -> Unit,
    private val onWriteCount: suspend (Int) -> Unit,
) : LogSink, AutoCloseable {
    private val channel = Channel<LogEvent>(capacity = LogLimits.AppBufferEventCount)
    private val worker = scope.launch { writeLoop() }

    override fun emit(event: LogEvent) {
        check(channel.trySend(event).isSuccess) { "Room log queue is full" }
    }

    override fun close() {
        channel.close()
        worker.cancel()
    }

    private suspend fun writeLoop() {
        for (firstEvent in channel) {
            val batch = collectBatch(firstEvent)
            runCatching { repository.insertEvents(batch) }
                .onSuccess { onWriteCount(batch.size) }
                .onFailure { error -> onWriteFailure(batch, error) }
        }
    }

    private suspend fun collectBatch(firstEvent: LogEvent): List<LogEvent> {
        val batch = mutableListOf(firstEvent)
        if (firstEvent.level == LogLevel.ERROR) return batch
        val deadline = System.currentTimeMillis() + LogLimits.AppWriteDelayMillis
        while (batch.size < LogLimits.AppWriteBatchEventCount) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val next = withTimeoutOrNull(remaining) { channel.receive() } ?: break
            batch += next
            if (next.level == LogLevel.ERROR) break
        }
        return batch
    }
}
