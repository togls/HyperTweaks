package io.github.togls.hypertweaks.logging.app.database

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface LogEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<LogEntryEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun count(): Long

    @Query("SELECT * FROM log_entries WHERE event_id = :eventId LIMIT 1")
    suspend fun findByEventId(eventId: String): LogEntryEntity?

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM log_entries WHERE timestamp_millis < :timestamp")
    suspend fun deleteBefore(timestamp: Long)

    @Query(
        """
        DELETE FROM log_entries
        WHERE id NOT IN (
            SELECT id FROM log_entries
            ORDER BY timestamp_millis DESC, id DESC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimToMaxRows(maxRows: Int)

    @Query(PagedQuery)
    fun pagingSource(
        source: String?,
        levels: List<String>,
        includeAllLevels: Boolean,
        packageName: String?,
        tag: String?,
        event: String?,
        sessionId: String?,
        keyword: String?,
        fromMillis: Long?,
        toMillis: Long?,
    ): PagingSource<Int, LogEntryEntity>

    @Query(DeleteFilteredQuery)
    suspend fun deleteFiltered(
        source: String?,
        levels: List<String>,
        includeAllLevels: Boolean,
        packageName: String?,
        tag: String?,
        event: String?,
        sessionId: String?,
        keyword: String?,
        fromMillis: Long?,
        toMillis: Long?,
    )

    companion object {
        const val FilterClause = """
            (:source IS NULL OR source = :source)
            AND (:includeAllLevels = 1 OR level IN (:levels))
            AND (:packageName IS NULL OR package_name = :packageName)
            AND (:tag IS NULL OR tag = :tag)
            AND (:event IS NULL OR event = :event)
            AND (:sessionId IS NULL OR session_id = :sessionId)
            AND (:keyword IS NULL OR message LIKE '%' || :keyword || '%'
                OR fields_json LIKE '%' || :keyword || '%'
                OR tag LIKE '%' || :keyword || '%'
                OR event LIKE '%' || :keyword || '%')
            AND (:fromMillis IS NULL OR timestamp_millis >= :fromMillis)
            AND (:toMillis IS NULL OR timestamp_millis <= :toMillis)
        """

        const val PagedQuery = """
            SELECT * FROM log_entries
            WHERE $FilterClause
            ORDER BY timestamp_millis DESC, id DESC
        """

        const val DeleteFilteredQuery = """
            DELETE FROM log_entries
            WHERE $FilterClause
        """
    }
}
