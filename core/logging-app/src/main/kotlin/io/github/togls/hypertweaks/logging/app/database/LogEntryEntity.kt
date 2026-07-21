package io.github.togls.hypertweaks.logging.app.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "log_entries",
    indices = [
        Index(value = ["event_id"], unique = true),
        Index(value = ["timestamp_millis"]),
        Index(value = ["source", "timestamp_millis"]),
        Index(value = ["level", "timestamp_millis"]),
        Index(value = ["package_name", "timestamp_millis"]),
        Index(value = ["tag", "timestamp_millis"]),
        Index(value = ["event", "timestamp_millis"]),
        Index(value = ["session_id", "timestamp_millis"]),
    ],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    @ColumnInfo(name = "elapsed_realtime")
    val elapsedRealtime: Long?,
    val source: String,
    val level: String,
    val tag: String,
    val event: String,
    val message: String?,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "process_name")
    val processName: String?,
    val pid: Int?,
    val tid: Int?,
    @ColumnInfo(name = "session_id")
    val sessionId: String?,
    @ColumnInfo(name = "fields_json")
    val fieldsJson: String,
    @ColumnInfo(name = "throwable_type")
    val throwableType: String?,
    @ColumnInfo(name = "throwable_message")
    val throwableMessage: String?,
    @ColumnInfo(name = "stack_trace")
    val stackTrace: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
