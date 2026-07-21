package io.github.togls.hypertweaks.feature.logviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems

@Composable
fun LogViewerRoute(
    onBack: () -> Unit,
    viewModel: LogViewerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val logs = viewModel.pagingData.collectAsLazyPagingItems()

    LogViewerScreen(
        uiState = uiState,
        logs = logs,
        onBack = onBack,
        onShowFilters = { viewModel.showFilters(true) },
        onDismissFilters = { viewModel.showFilters(false) },
        onApplyFilters = viewModel::updateFilters,
        onResetFilters = viewModel::resetFilters,
        onSelectEvent = viewModel::selectEvent,
        onShowClearOptions = { viewModel.showClearOptions(true) },
        onDismissClearOptions = { viewModel.showClearOptions(false) },
        onRequestClear = viewModel::requestClear,
        onConfirmClear = viewModel::confirmClear,
        onDismissMessage = viewModel::dismissMessage,
    )
}
