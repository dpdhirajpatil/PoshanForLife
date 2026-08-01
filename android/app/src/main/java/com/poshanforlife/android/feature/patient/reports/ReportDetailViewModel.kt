package com.poshanforlife.android.feature.patient.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.data.HealthRecordRepository
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.core.network.HealthRecordDto
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

/**
 * Chart window for AN-05's trend section. [recordLimit] is a record count, not a day
 * count — the backend has no date-range filter on this endpoint, and records are at
 * most one per day, so "most recent N records" is the closest available approximation
 * of "last N days" (it reads longer than N days back when the patient logged sparsely).
 */
enum class TrendWindow(val label: String, val recordLimit: Int) {
    DAYS_30("30d", 30),
    DAYS_90("90d", 90),
    DAYS_180("180d", 180),
    ALL("All", Int.MAX_VALUE),
}

sealed class TrendsUiState {
    data object Loading : TrendsUiState()

    /** Records are chronological ascending, exactly as the backend returned them. */
    data class Success(val records: List<HealthRecordDto>) : TrendsUiState()
    data class Error(val message: String) : TrendsUiState()
}

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val healthRecordRepository: HealthRecordRepository,
    authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val _uiState = MutableStateFlow<ReportDetailUiState>(ReportDetailUiState.Loading)
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    private val _trends = MutableStateFlow<TrendsUiState>(TrendsUiState.Loading)
    val trends: StateFlow<TrendsUiState> = _trends.asStateFlow()

    private val _trendWindow = MutableStateFlow(TrendWindow.DAYS_90)
    val trendWindow: StateFlow<TrendWindow> = _trendWindow.asStateFlow()

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
                is Result.Success -> {
                    _uiState.update { ReportDetailUiState.Success(result.data) }
                    // patientId only becomes known once the report loads, so the trend
                    // fetch is chained here rather than started in init.
                    result.data.patient?.id?.let { loadTrends(it) }
                }
                is Result.Error -> _uiState.update { ReportDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onTrendWindowChange(window: TrendWindow) {
        if (_trendWindow.value == window) return
        _trendWindow.update { window }
        currentPatientId()?.let { loadTrends(it) }
    }

    private fun currentPatientId(): String? = (_uiState.value as? ReportDetailUiState.Success)?.report?.patient?.id

    private fun loadTrends(patientId: String) {
        viewModelScope.launch {
            _trends.update { TrendsUiState.Loading }
            when (val result = healthRecordRepository.trends(patientId, _trendWindow.value.recordLimit)) {
                is Result.Success -> _trends.update { TrendsUiState.Success(result.data) }
                is Result.Error -> _trends.update { TrendsUiState.Error(result.message) }
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
