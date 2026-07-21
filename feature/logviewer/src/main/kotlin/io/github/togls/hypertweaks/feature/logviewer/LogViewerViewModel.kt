package io.github.togls.hypertweaks.feature.logviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.app.AppLogRuntime
import io.github.togls.hypertweaks.logging.app.LogDatabaseState
import io.github.togls.hypertweaks.logging.app.repository.LogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val filters = MutableStateFlow(LogFilterState())
    private val selectedEvent = MutableStateFlow<LogEvent?>(null)
    private val dialogs = MutableStateFlow(DialogState())
    private val operationMessage = MutableStateFlow<String?>(null)
    private val uiStateFlow = MutableStateFlow(LogViewerUiState())

    val uiState: StateFlow<LogViewerUiState> = uiStateFlow.asStateFlow()

    val pagingData = combine(AppLogRuntime.databaseState, filters) { databaseState, filterState ->
        databaseState to filterState
    }.flatMapLatest { (databaseState, filterState) ->
        val repository = (databaseState as? LogDatabaseState.Ready)?.repository
        repository?.pagedLogs(filterState.toQuery()) ?: flowOf(PagingData.empty())
    }.cachedIn(viewModelScope)

    init {
        AppLogRuntime.initialize(application)
        viewModelScope.launch {
            combine(
                AppLogRuntime.databaseState,
                AppLogRuntime.logMode,
                filters,
                selectedEvent,
                dialogs,
                operationMessage,
            ) { values -> buildUiState(values) }
                .collect { state -> uiStateFlow.value = state }
        }
    }

    fun updateFilters(nextFilters: LogFilterState) {
        filters.value = nextFilters
    }

    fun resetFilters() {
        filters.value = LogFilterState()
    }

    fun showFilters(show: Boolean) {
        dialogs.value = dialogs.value.copy(showFilters = show)
    }

    fun showClearOptions(show: Boolean) {
        dialogs.value = dialogs.value.copy(showClearOptions = show)
    }

    fun selectEvent(event: LogEvent?) {
        selectedEvent.value = event
    }

    fun requestClear(action: ClearLogsAction?) {
        dialogs.value = dialogs.value.copy(
            showClearOptions = false,
            pendingClearAction = action,
        )
    }

    fun confirmClear() {
        val action = dialogs.value.pendingClearAction ?: return
        dialogs.value = dialogs.value.copy(pendingClearAction = null)
        viewModelScope.launch { executeClear(action) }
    }

    fun dismissMessage() {
        operationMessage.value = null
    }

    private suspend fun executeClear(action: ClearLogsAction) {
        val repository = currentRepository() ?: run {
            operationMessage.value = "database_unavailable"
            return
        }
        runCatching {
            when (action) {
                ClearLogsAction.ALL -> repository.deleteAll()
                ClearLogsAction.CURRENT_FILTER -> repository.delete(filters.value.toQuery())
                ClearLogsAction.OLDER_THAN_7_DAYS -> repository.deleteBefore(daysAgo(7))
                ClearLogsAction.OLDER_THAN_30_DAYS -> repository.deleteBefore(daysAgo(30))
            }
        }.onSuccess {
            operationMessage.value = "logs_cleared"
        }.onFailure { error ->
            operationMessage.value = error.message ?: "clear_failed"
        }
    }

    private fun currentRepository(): LogRepository? {
        return (AppLogRuntime.databaseState.value as? LogDatabaseState.Ready)?.repository
    }

    private fun buildUiState(values: Array<Any?>): LogViewerUiState {
        val databaseState = values[0] as LogDatabaseState
        val dialogState = values[4] as DialogState
        return LogViewerUiState(
            databaseStatus = databaseState.toUiStatus(),
            databaseError = (databaseState as? LogDatabaseState.Failed)?.message,
            logMode = values[1] as io.github.togls.hypertweaks.logging.api.LogMode,
            filters = values[2] as LogFilterState,
            selectedEvent = values[3] as LogEvent?,
            showFilters = dialogState.showFilters,
            showClearOptions = dialogState.showClearOptions,
            pendingClearAction = dialogState.pendingClearAction,
            operationMessage = values[5] as String?,
        )
    }

    private fun LogDatabaseState.toUiStatus(): LogViewerDatabaseStatus {
        return when (this) {
            LogDatabaseState.Initializing -> LogViewerDatabaseStatus.INITIALIZING
            LogDatabaseState.Recovering -> LogViewerDatabaseStatus.RECOVERING
            is LogDatabaseState.Ready -> LogViewerDatabaseStatus.READY
            is LogDatabaseState.Failed -> LogViewerDatabaseStatus.FAILED
        }
    }

    private fun daysAgo(days: Int): Long {
        return System.currentTimeMillis() - days * 24L * 60L * 60L * 1_000L
    }
}

private data class DialogState(
    val showFilters: Boolean = false,
    val showClearOptions: Boolean = false,
    val pendingClearAction: ClearLogsAction? = null,
)
