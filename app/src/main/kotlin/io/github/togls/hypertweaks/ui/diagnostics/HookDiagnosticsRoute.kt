package io.github.togls.hypertweaks.ui.diagnostics

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HookDiagnosticsRoute(
    onBack: () -> Unit,
    viewModel: HookDiagnosticsViewModel = viewModel(),
) {
    HookDiagnosticsScreen(
        uiState = viewModel.uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
    )
}
