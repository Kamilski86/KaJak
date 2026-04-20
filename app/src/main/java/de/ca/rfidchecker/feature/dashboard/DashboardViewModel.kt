package de.ca.rfidchecker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ca.rfidchecker.data.repository.MismatchRepository
import de.ca.rfidchecker.domain.model.MismatchRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val scansToday: Int = 0,
    val mismatchesToday: Int = 0,
    val recentMismatches: List<MismatchRecord> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MismatchRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTodayCount().collect { count ->
                _uiState.value = _uiState.value.copy(mismatchesToday = count)
            }
        }
        viewModelScope.launch {
            repository.observeLatest(10).collect { latest ->
                _uiState.value = _uiState.value.copy(recentMismatches = latest)
            }
        }
        viewModelScope.launch {
            repository.observeTodayScanCount().collect { count ->
                _uiState.value = _uiState.value.copy(scansToday = count)
            }
        }
    }
}
