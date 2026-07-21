package io.github.togls.hypertweaks.feature.logviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.togls.hypertweaks.feature.logviewer.component.ClearConfirmationDialog
import io.github.togls.hypertweaks.feature.logviewer.component.ClearOptionsDialog
import io.github.togls.hypertweaks.feature.logviewer.component.LogEntryCard
import io.github.togls.hypertweaks.feature.logviewer.component.LogFilterDialog
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogMode

@Composable
fun LogViewerScreen(
    uiState: LogViewerUiState,
    logs: LazyPagingItems<LogEvent>,
    onBack: () -> Unit,
    onShowFilters: () -> Unit,
    onDismissFilters: () -> Unit,
    onApplyFilters: (LogFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onSelectEvent: (LogEvent?) -> Unit,
    onShowClearOptions: () -> Unit,
    onDismissClearOptions: () -> Unit,
    onRequestClear: (ClearLogsAction?) -> Unit,
    onConfirmClear: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    OperationMessage(uiState.operationMessage, snackbarHostState, onDismissMessage)
    Scaffold(
        topBar = {
            LogViewerTopBar(onBack, onShowFilters, onShowClearOptions)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LogViewerContent(uiState, logs, onSelectEvent, padding)
    }
    LogViewerDialogs(
        uiState = uiState,
        onDismissFilters = onDismissFilters,
        onApplyFilters = onApplyFilters,
        onResetFilters = onResetFilters,
        onSelectEvent = onSelectEvent,
        onDismissClearOptions = onDismissClearOptions,
        onRequestClear = onRequestClear,
        onConfirmClear = onConfirmClear,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LogViewerTopBar(
    onBack: () -> Unit,
    onShowFilters: () -> Unit,
    onShowClearOptions: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.log_viewer_title)) },
        navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        },
        actions = {
            TextButton(onClick = onShowFilters) { Text(stringResource(R.string.action_filters)) }
            TextButton(onClick = onShowClearOptions) { Text(stringResource(R.string.action_clear)) }
        },
    )
}

@Composable
private fun LogViewerContent(
    uiState: LogViewerUiState,
    logs: LazyPagingItems<LogEvent>,
    onSelectEvent: (LogEvent?) -> Unit,
    padding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        StatusBanner(uiState)
        when {
            uiState.databaseStatus == LogViewerDatabaseStatus.FAILED -> FailureState(uiState)
            logs.loadState.refresh is LoadState.Loading -> LoadingState()
            logs.loadState.refresh is LoadState.Error -> PagingFailure(logs)
            logs.itemCount == 0 -> EmptyState(uiState.filters.isDefault)
            else -> LogList(logs, onSelectEvent)
        }
    }
}

@Composable
private fun LogList(logs: LazyPagingItems<LogEvent>, onSelectEvent: (LogEvent?) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = logs.itemCount,
            key = { index -> logs.peek(index)?.eventId ?: index },
        ) { index ->
            logs[index]?.let { event -> LogEntryCard(event, onClick = { onSelectEvent(event) }) }
        }
        if (logs.loadState.append is LoadState.Loading) {
            item { LoadingState() }
        }
    }
}

@Composable
private fun StatusBanner(uiState: LogViewerUiState) {
    val message = when {
        uiState.logMode == LogMode.OFF -> stringResource(R.string.status_logging_off)
        uiState.databaseStatus == LogViewerDatabaseStatus.RECOVERING -> {
            stringResource(R.string.status_database_recovering)
        }
        uiState.databaseStatus == LogViewerDatabaseStatus.INITIALIZING -> {
            stringResource(R.string.status_database_initializing)
        }
        else -> null
    }
    if (message != null) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailureState(uiState: LogViewerUiState) {
    StateText(stringResource(R.string.status_database_failed, uiState.databaseError.orEmpty()))
}

@Composable
private fun PagingFailure(logs: LazyPagingItems<LogEvent>) {
    StateText((logs.loadState.refresh as LoadState.Error).error.message ?: stringResource(R.string.status_load_failed))
}

@Composable
private fun EmptyState(defaultFilters: Boolean) {
    StateText(
        stringResource(if (defaultFilters) R.string.status_no_logs else R.string.status_no_filter_results),
    )
}

@Composable
private fun StateText(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LogViewerDialogs(
    uiState: LogViewerUiState,
    onDismissFilters: () -> Unit,
    onApplyFilters: (LogFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onSelectEvent: (LogEvent?) -> Unit,
    onDismissClearOptions: () -> Unit,
    onRequestClear: (ClearLogsAction?) -> Unit,
    onConfirmClear: () -> Unit,
) {
    if (uiState.showFilters) {
        LogFilterDialog(uiState.filters, onDismissFilters, onApplyFilters, onResetFilters)
    }
    uiState.selectedEvent?.let { event ->
        LogDetailDialog(
            event = event,
            onDismiss = { onSelectEvent(null) },
            onFilterTag = {
                onApplyFilters(uiState.filters.copy(tag = event.tag))
                onSelectEvent(null)
            },
            onFilterSession = event.sessionId?.let { sessionId ->
                {
                    onApplyFilters(uiState.filters.copy(sessionId = sessionId))
                    onSelectEvent(null)
                }
            },
        )
    }
    if (uiState.showClearOptions) {
        ClearOptionsDialog(onDismissClearOptions, onRequestClear)
    }
    uiState.pendingClearAction?.let { action ->
        ClearConfirmationDialog(action, onDismiss = { onRequestClear(null) }, onConfirmClear)
    }
}

@Composable
private fun OperationMessage(
    message: String?,
    hostState: SnackbarHostState,
    onDismissMessage: () -> Unit,
) {
    val displayMessage = when (message) {
        null -> null
        "logs_cleared" -> stringResource(R.string.status_logs_cleared)
        "database_unavailable" -> stringResource(R.string.status_database_unavailable)
        "clear_failed" -> stringResource(R.string.status_clear_failed)
        else -> message
    }
    LaunchedEffect(displayMessage) {
        if (displayMessage != null) {
            hostState.showSnackbar(displayMessage)
            onDismissMessage()
        }
    }
}
