package io.github.togls.hypertweaks.logging.app

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLimits
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.logging.api.NoOpLogger
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.app.database.HyperTweaksLogDatabase
import io.github.togls.hypertweaks.logging.app.ingest.LogBridgeVisibilityGranter
import io.github.togls.hypertweaks.logging.app.repository.LogRepository
import io.github.togls.hypertweaks.logging.app.repository.RoomLogRepository
import io.github.togls.hypertweaks.logging.app.sink.AndroidLogSink
import io.github.togls.hypertweaks.logging.app.sink.AppBufferSink
import io.github.togls.hypertweaks.logging.app.sink.RoomLogSink
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AppLogRuntime {
    private val initialized = AtomicBoolean(false)
    private val recoveryScheduled = AtomicBoolean(false)
    private val mode = AtomicReference(LogMode.Default)
    private val modeStateFlow = MutableStateFlow(LogMode.Default)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseStateFlow = MutableStateFlow<LogDatabaseState>(LogDatabaseState.Initializing)
    private val roomRouter = RecoveringRoomSink()
    private var database: HyperTweaksLogDatabase? = null
    private var modeCache: LogModeCache? = null
    private var loggerReference: Logger? = null
    private var applicationContext: Context? = null
    private var writtenSinceCleanup = 0

    val databaseState: StateFlow<LogDatabaseState> = databaseStateFlow.asStateFlow()
    val logMode: StateFlow<LogMode> = modeStateFlow.asStateFlow()

    val currentMode: LogMode
        get() = mode.get()

    val logger: Logger
        get() = loggerReference ?: NoOpLogger

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        runCatching { initializeInternal(context) }
            .onFailure { error ->
                loggerReference = NoOpLogger
                databaseStateFlow.value = LogDatabaseState.Failed(
                    error.message ?: "Logging runtime initialization failed",
                )
                Log.e("HyperTweaks", "logging.runtime.failed", error)
            }
    }

    private fun initializeInternal(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        modeCache = LogModeCache(appContext).also { cache ->
            val cachedMode = cache.read()
            mode.set(cachedMode)
            modeStateFlow.value = cachedMode
        }
        loggerReference = AppLogger.create(
            modeProvider = mode::get,
            androidSink = AndroidLogSink(),
            roomSink = roomRouter,
            context = createAppContext(appContext),
        )
        grantBridgeVisibility(appContext)
        logger.info("module.load.succeeded", "HyperTweaks app logging initialized")
        scope.launch { openDatabase() }
    }

    private fun grantBridgeVisibility(context: Context) {
        val summary = LogBridgeVisibilityGranter.grant(context)
        logger.info(
            event = "provider.visibility.grant.completed",
            fields = mapOf(
                "requested_count" to summary.requestedPackageCount.toString(),
                "installed_count" to summary.installedPackageCount.toString(),
                "granted_count" to summary.grantedPackages.size.toString(),
                "failed_count" to summary.failures.size.toString(),
            ),
        )
        if (summary.failures.isEmpty()) return
        logger.warn(
            event = "provider.visibility.failed",
            fields = mapOf(
                "failures" to summary.failures.entries.joinToString { (packageName, errorType) ->
                    "$packageName:$errorType"
                },
            ),
        )
    }

    fun updateMode(nextMode: LogMode, persistCache: Boolean = true): Result<LogMode> {
        return runCatching {
            mode.set(nextMode)
            modeStateFlow.value = nextMode
            if (persistCache) checkNotNull(modeCache).write(nextMode)
            nextMode
        }
    }

    fun ingest(events: List<LogEvent>): Int {
        return countAcceptedPrefix(events, roomRouter::offer)
    }

    fun runWhenDatabaseReady(action: () -> Unit) {
        scope.launch {
            databaseStateFlow.first { state -> state is LogDatabaseState.Ready }
            runCatching(action)
                .onFailure { error -> infrastructureError("database.ready.action.failed", error) }
        }
    }

    private suspend fun openDatabase() {
        val context = applicationContext ?: return
        databaseStateFlow.value = LogDatabaseState.Recovering
        runCatching { HyperTweaksLogDatabase.open(context) }
            .onSuccess { openedDatabase -> installDatabase(openedDatabase) }
            .onFailure(::handleDatabaseOpenFailure)
    }

    private suspend fun installDatabase(openedDatabase: HyperTweaksLogDatabase) {
        database?.close()
        database = openedDatabase
        val repository = RoomLogRepository(openedDatabase)
        val sink = RoomLogSink(
            scope = scope,
            repository = repository,
            onWriteFailure = ::handleDatabaseWriteFailure,
            onWriteCount = { count -> afterSuccessfulWrite(repository, count) },
        )
        roomRouter.install(sink)
        databaseStateFlow.value = LogDatabaseState.Ready(repository)
        recoveryScheduled.set(false)
        runRetentionCleanup(repository, force = false)
        logger.info("database.open.succeeded")
    }

    private fun handleDatabaseOpenFailure(error: Throwable) {
        infrastructureError("database.open.failed", error)
        databaseStateFlow.value = LogDatabaseState.Failed(error.message ?: "Database open failed")
        scheduleRecovery()
    }

    private fun handleDatabaseWriteFailure(events: List<LogEvent>, error: Throwable) {
        infrastructureError("database.write.failed", error)
        roomRouter.uninstall()
        events.forEach(roomRouter::offer)
        databaseStateFlow.value = LogDatabaseState.Failed(error.message ?: "Database write failed")
        scheduleRecovery()
    }

    private suspend fun afterSuccessfulWrite(repository: LogRepository, count: Int) {
        writtenSinceCleanup += count
        if (writtenSinceCleanup < CleanupWriteThreshold) return
        writtenSinceCleanup = 0
        runRetentionCleanup(repository, force = true)
    }

    private suspend fun runRetentionCleanup(repository: LogRepository, force: Boolean) {
        val now = System.currentTimeMillis()
        val cache = modeCache ?: return
        if (!force && !cache.shouldRunCleanup(now)) return
        val cutoff = now - LogLimits.MaxRetentionDays * MillisPerDay
        runCatching {
            repository.deleteBefore(cutoff)
            repository.trimToMaxRows(LogLimits.MaxDatabaseRows)
            cache.recordCleanup(now)
        }.onFailure { error -> infrastructureError("database.cleanup.failed", error) }
    }

    private fun scheduleRecovery() {
        if (!recoveryScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(DatabaseRetryMillis)
            recoveryScheduled.set(false)
            openDatabase()
        }
    }

    private fun createAppContext(context: Context): LogContext {
        return LogContext(
            tag = "App",
            packageName = context.packageName,
            processName = Application.getProcessName(),
            pid = Process.myPid(),
            tid = Process.myTid(),
            sessionId = UUID.randomUUID().toString(),
        )
    }

    private fun infrastructureError(event: String, error: Throwable) {
        Log.e("HyperTweaks", "$event: ${error.message}", error)
    }

    private const val CleanupWriteThreshold = 1_000
    private const val MillisPerDay = 24L * 60L * 60L * 1_000L
    private const val DatabaseRetryMillis = 5_000L
}

internal fun countAcceptedPrefix(
    events: List<LogEvent>,
    offer: (LogEvent) -> Boolean,
): Int {
    var acceptedCount = 0
    for (event in events) {
        if (!offer(event)) break
        acceptedCount++
    }
    return acceptedCount
}

sealed interface LogDatabaseState {
    data object Initializing : LogDatabaseState
    data object Recovering : LogDatabaseState
    data class Ready(val repository: LogRepository) : LogDatabaseState
    data class Failed(val message: String) : LogDatabaseState
}

private class RecoveringRoomSink : LogSink {
    private val currentSink = AtomicReference<RoomLogSink?>(null)
    private val buffer = AppBufferSink()

    override fun emit(event: LogEvent) {
        offer(event)
    }

    fun offer(event: LogEvent): Boolean {
        val sink = currentSink.get()
        if (sink == null) return buffer.offer(event)
        return runCatching { sink.emit(event) }.fold(
            onSuccess = { true },
            onFailure = { buffer.offer(event) },
        )
    }

    fun install(sink: RoomLogSink) {
        currentSink.getAndSet(sink)?.close()
        buffer.drain().forEach { event ->
            runCatching { sink.emit(event) }
                .onFailure { buffer.offer(event) }
        }
    }

    fun uninstall() {
        currentSink.getAndSet(null)?.close()
    }
}
