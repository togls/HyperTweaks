package io.github.togls.hypertweaks.feature.logviewer

import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogMode

data class LogViewerUiState(
    val databaseStatus: LogViewerDatabaseStatus = LogViewerDatabaseStatus.INITIALIZING,
    val databaseError: String? = null,
    val logMode: LogMode = LogMode.Default,
    val filters: LogFilterState = LogFilterState(),
    val selectedEvent: LogEvent? = null,
    val showFilters: Boolean = false,
    val showClearOptions: Boolean = false,
    val pendingClearAction: ClearLogsAction? = null,
    val operationMessage: String? = null,
)

enum class LogViewerDatabaseStatus {
    INITIALIZING,
    READY,
    RECOVERING,
    FAILED,
}

enum class ClearLogsAction {
    ALL,
    CURRENT_FILTER,
    OLDER_THAN_7_DAYS,
    OLDER_THAN_30_DAYS,
}
