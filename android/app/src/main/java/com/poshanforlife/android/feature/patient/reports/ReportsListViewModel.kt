package com.poshanforlife.android.feature.patient.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.network.ReportListItemDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReportsListUiState {
    data object Loading : ReportsListUiState()
    data class Success(val reports: List<ReportListItemDto>) : ReportsListUiState()
    data class Error(val message: String) : ReportsListUiState()
}

@HiltViewModel
class ReportsListViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportsListUiState>(ReportsListUiState.Loading)
    val uiState: StateFlow<ReportsListUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = reportRepository.listInBodyReports()) {
                is Result.Success -> _uiState.update { ReportsListUiState.Success(result.data) }
                is Result.Error -> _uiState.update { ReportsListUiState.Error(result.message) }
                Result.Loading -> Unit
            }
            _isRefreshing.value = false
        }
    }
}
