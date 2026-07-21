package io.github.togls.hypertweaks.logging.app.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface LogSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: LogSessionEntity)

    @Query(
        """
        UPDATE log_sessions
        SET ended_at = :endedAt,
            status = :status,
            debug_count = :debugCount,
            info_count = :infoCount,
            warn_count = :warnCount,
            error_count = :errorCount
        WHERE session_id = :sessionId
        """,
    )
    suspend fun updateSessionSummary(
        sessionId: String,
        endedAt: Long?,
        status: String,
        debugCount: Long,
        infoCount: Long,
        warnCount: Long,
        errorCount: Long,
    )

    @Query(
        """
        UPDATE log_sessions
        SET ended_at = :lastEventAt,
            debug_count = debug_count + :debugCount,
            info_count = info_count + :infoCount,
            warn_count = warn_count + :warnCount,
            error_count = error_count + :errorCount
        WHERE session_id = :sessionId
        """,
    )
    suspend fun incrementSessionSummary(
        sessionId: String,
        lastEventAt: Long,
        debugCount: Long,
        infoCount: Long,
        warnCount: Long,
        errorCount: Long,
    )
}
