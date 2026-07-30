package com.poshanforlife.android.feature.patient.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReportDetailUiState {
    data object Loading : ReportDetailUiState()
    data class Success(val report: ReportDetailDto) : ReportDetailUiState()
    data class Error(val message: String) : ReportDetailUiState()
}

sealed class ReportDeleteState {
    data object Idle : ReportDeleteState()
    data object Deleting : ReportDeleteState()
    data object Deleted : ReportDeleteState()
    data class Error(val message: String) : ReportDeleteState()
}

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val _uiState = MutableStateFlow<ReportDetailUiState>(ReportDetailUiState.Loading)
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    /** Gates the edit/delete actions — a PATIENT reusing this same route/screen never sees them. */
    val currentUserRole: StateFlow<Role> = authRepository.currentUser()
        .map { Role.fromWire(it?.role) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Role.UNKNOWN)

    private val _deleteState = MutableStateFlow<ReportDeleteState>(ReportDeleteState.Idle)
    val deleteState: StateFlow<ReportDeleteState> = _deleteState.asStateFlow()

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

    fun delete() {
        viewModelScope.launch {
            _deleteState.update { ReportDeleteState.Deleting }
            when (val result = reportRepository.deleteReport(reportId)) {
                is Result.Success -> _deleteState.update { ReportDeleteState.Deleted }
                is Result.Error -> _deleteState.update { ReportDeleteState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
