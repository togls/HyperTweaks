package io.github.togls.hypertweaks.logging.app.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.app.database.HyperTweaksLogDatabase
import io.github.togls.hypertweaks.logging.app.database.LogEntityMapper
import io.github.togls.hypertweaks.logging.app.database.LogSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLogRepository(
    database: HyperTweaksLogDatabase,
) : LogRepository {
    private val entryDao = database.logEntryDao()
    private val sessionDao = database.logSessionDao()

    override fun pagedLogs(query: LogQuery): Flow<PagingData<LogEvent>> {
        val normalized = query.normalized()
        return Pager(PagingConfig(pageSize = PageSize, enablePlaceholders = false)) {
            entryDao.pagingSource(
                source = normalized.source?.name,
                levels = normalized.levels.map(LogLevel::name),
                includeAllLevels = normalized.levels.size == LogLevel.entries.size,
                packageName = normalized.packageName,
                tag = normalized.tag,
                event = normalized.event,
                sessionId = normalized.sessionId,
                keyword = normalized.keyword,
                fromMillis = normalized.fromMillis,
                toMillis = normalized.toMillis,
            )
        }.flow.map { pagingData -> pagingData.map(LogEntityMapper::toEvent) }
    }

    override suspend fun insertEvents(events: List<LogEvent>) {
        if (events.isEmpty()) return
        val insertResults = entryDao.insertEntries(events.map(LogEntityMapper::toEntity))
        val insertedEvents = events.zip(insertResults)
            .filter { (_, rowId) -> rowId != -1L }
            .map { (event, _) -> event }
        insertedEvents.groupBy(LogEvent::sessionId)
            .filterKeys { sessionId -> sessionId != null }
            .forEach { (sessionId, sessionEvents) ->
                updateSession(checkNotNull(sessionId), sessionEvents)
            }
    }

    override suspend fun insertSession(session: LogSessionEntity) {
        sessionDao.insertSession(session)
    }

    override suspend fun deleteAll() = entryDao.deleteAll()

    override suspend fun delete(query: LogQuery) {
        val normalized = query.normalized()
        entryDao.deleteFiltered(
            source = normalized.source?.name,
            levels = normalized.levels.map(LogLevel::name),
            includeAllLevels = normalized.levels.size == LogLevel.entries.size,
            packageName = normalized.packageName,
            tag = normalized.tag,
            event = normalized.event,
            sessionId = normalized.sessionId,
            keyword = normalized.keyword,
            fromMillis = normalized.fromMillis,
            toMillis = normalized.toMillis,
        )
    }

    override suspend fun deleteBefore(timestamp: Long) = entryDao.deleteBefore(timestamp)

    override suspend fun trimToMaxRows(maxRows: Int) = entryDao.trimToMaxRows(maxRows)

    override suspend fun count(): Long = entryDao.count()

    private suspend fun updateSession(sessionId: String, events: List<LogEvent>) {
        val firstEvent = events.first()
        sessionDao.insertSession(
            LogSessionEntity(
                sessionId = sessionId,
                source = firstEvent.source.name,
                packageName = firstEvent.packageName,
                processName = firstEvent.processName,
                startedAt = events.minOf(LogEvent::timestampMillis),
                endedAt = null,
                moduleVersion = null,
                targetVersion = null,
                logMode = firstEvent.fields["log_mode"] ?: "unknown",
                status = "active",
            ),
        )
        sessionDao.incrementSessionSummary(
            sessionId = sessionId,
            lastEventAt = events.maxOf(LogEvent::timestampMillis),
            debugCount = events.countLevel(LogLevel.DEBUG),
            infoCount = events.countLevel(LogLevel.INFO),
            warnCount = events.countLevel(LogLevel.WARN),
            errorCount = events.countLevel(LogLevel.ERROR),
        )
    }

    private fun List<LogEvent>.countLevel(level: LogLevel): Long {
        return count { event -> event.level == level }.toLong()
    }

    private companion object {
        const val PageSize = 50
    }
}
