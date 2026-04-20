package de.ca.rfidchecker.feature.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ca.rfidchecker.data.repository.MismatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val lastExportPath: String? = null,
    val message: String? = null,
    val isExporting: Boolean = false
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    application: Application,
    private val repository: MismatchRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun exportCsv() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            repository.exportPendingCsv(getApplication()).onSuccess { path ->
                _uiState.value = _uiState.value.copy(lastExportPath = path, message = "CSV stored in documents", isExporting = false)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(message = ex.message ?: "Export fehlgeschlagen", isExporting = false)
            }
        }
    }
}
