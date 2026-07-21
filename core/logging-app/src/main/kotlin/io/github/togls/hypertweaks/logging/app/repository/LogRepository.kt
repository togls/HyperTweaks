package io.github.togls.hypertweaks.logging.app.repository

import androidx.paging.PagingData
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.app.database.LogSessionEntity
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun pagedLogs(query: LogQuery): Flow<PagingData<LogEvent>>

    suspend fun insertEvents(events: List<LogEvent>)

    suspend fun insertSession(session: LogSessionEntity)

    suspend fun deleteAll()

    suspend fun delete(query: LogQuery)

    suspend fun deleteBefore(timestamp: Long)

    suspend fun trimToMaxRows(maxRows: Int)

    suspend fun count(): Long
}
