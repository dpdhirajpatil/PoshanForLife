package com.poshanforlife.android.feature.patient.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReportDetailUiState {
    data object Loading : ReportDetailUiState()
    data class Success(val report: ReportDetailDto) : ReportDetailUiState()
    data class Error(val message: String) : ReportDetailUiState()
}

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val _uiState = MutableStateFlow<ReportDetailUiState>(ReportDetailUiState.Loading)
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { ReportDetailUiState.Loading }
            when (val result = reportRepository.getReport(reportId)) {
                is Result.Success -> _uiState.update { ReportDetailUiState.Success(result.data) }
                is Result.Error -> _uiState.update { ReportDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
