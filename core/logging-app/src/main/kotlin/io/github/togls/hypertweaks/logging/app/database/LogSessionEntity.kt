package io.github.togls.hypertweaks.logging.app.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "log_sessions")
data class LogSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val source: String,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "process_name")
    val processName: String?,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,
    @ColumnInfo(name = "module_version")
    val moduleVersion: String?,
    @ColumnInfo(name = "target_version")
    val targetVersion: String?,
    @ColumnInfo(name = "log_mode")
    val logMode: String,
    val status: String,
    @ColumnInfo(name = "debug_count")
    val debugCount: Long = 0L,
    @ColumnInfo(name = "info_count")
    val infoCount: Long = 0L,
    @ColumnInfo(name = "warn_count")
    val warnCount: Long = 0L,
    @ColumnInfo(name = "error_count")
    val errorCount: Long = 0L,
)
