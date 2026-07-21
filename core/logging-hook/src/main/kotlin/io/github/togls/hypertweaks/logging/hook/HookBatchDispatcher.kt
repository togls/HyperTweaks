package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogEventBuilder
import io.github.togls.hypertweaks.logging.api.LogIngestResult
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogLimits
import io.github.togls.hypertweaks.logging.api.LogSource
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun interface HookBatchTransport {
    fun send(events: List<LogEvent>): LogIngestResult
}

class HookBatchDispatcher(
    private val buffer: HookEventBuffer,
    private val transport: HookBatchTransport,
    private val executor: ScheduledExecutorService = createExecutor(),
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val flushRequested = AtomicBoolean(false)
    private val retryBackoff = RetryBackoff()
    private var nextAttemptMillis = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        executor.scheduleWithFixedDelay(
            ::flushSafely,
            LogLimits.IpcFlushDelayMillis,
            LogLimits.IpcFlushDelayMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    fun enqueue(event: LogEvent): Boolean {
        val accepted = buffer.offer(event)
        if (accepted && event.level == LogLevel.ERROR && started.get()) {
            executor.execute(::flushSafely)
        }
        return accepted
    }

    fun requestFlush() {
        if (!started.get() || !flushRequested.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    if (!started.get()) return@execute
                    retryBackoff.reset()
                    nextAttemptMillis = 0L
                    flushSafely()
                } finally {
                    flushRequested.set(false)
                }
            }
        } catch (error: RejectedExecutionException) {
            flushRequested.set(false)
            if (started.get()) throw error
        }
    }

    override fun close() {
        started.set(false)
        executor.shutdownNow()
    }

    internal fun flushSafely() {
        if (!started.get() || clock() < nextAttemptMillis) return
        val batch = buffer.peekBatch()
        if (batch.isEmpty()) return
        val result = runCatching { transport.send(batch) }
            .getOrElse { LogIngestResult(0, batch.size, true, "transport_exception") }
        handleResult(batch, result)
    }

    private fun handleResult(batch: List<LogEvent>, result: LogIngestResult) {
        val acceptedCount = result.acceptedCount.coerceIn(0, batch.size)
        if (acceptedCount > 0) buffer.acknowledge(acceptedCount)
        if (result.retryable || acceptedCount < batch.size) {
            nextAttemptMillis = clock() + retryBackoff.nextDelayMillis()
            return
        }
        retryBackoff.reset()
        nextAttemptMillis = 0L
        enqueueDroppedSummary(batch.last())
    }

    private fun enqueueDroppedSummary(reference: LogEvent) {
        val droppedCount = buffer.consumeDroppedCount()
        if (droppedCount == 0L) return
        val summary = LogEventBuilder(LogSource.HOOK).build(
            context = LogContext(
                tag = "HookLogBridge",
                packageName = reference.packageName,
                processName = reference.processName,
                pid = reference.pid,
            ),
            level = LogLevel.WARN,
            event = "buffer.events.dropped",
            message = "Hook log buffer dropped events",
            throwable = null,
            fields = mapOf("dropped_count" to droppedCount.toString()),
        )
        buffer.offer(summary)
    }

    companion object {
        private fun createExecutor(): ScheduledExecutorService {
            return Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "hypertweaks-log-bridge").apply { isDaemon = true }
            }
        }
    }
}

class RetryBackoff(
    private val delaysMillis: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L),
) {
    private var index = 0

    @Synchronized
    fun nextDelayMillis(): Long {
        val delay = delaysMillis[index.coerceAtMost(delaysMillis.lastIndex)]
        if (index < delaysMillis.lastIndex) index++
        return delay
    }

    @Synchronized
    fun reset() {
        index = 0
    }
}
