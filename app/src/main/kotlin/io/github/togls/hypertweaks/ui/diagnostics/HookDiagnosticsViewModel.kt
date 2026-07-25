package io.github.togls.hypertweaks.ui.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HookDiagnosticsViewModel : ViewModel() {
    private val loader = HookDiagnosticsLoader()
    var uiState by mutableStateOf(HookDiagnosticsUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        uiState = uiState.copy(loading = true, errorMessage = null)
        viewModelScope.launch {
            uiState = loader.load()
        }
    }
}
