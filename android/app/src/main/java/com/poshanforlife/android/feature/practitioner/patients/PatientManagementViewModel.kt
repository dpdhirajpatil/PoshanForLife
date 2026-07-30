package com.poshanforlife.android.feature.practitioner.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.network.PatientDetailDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.PatientSummaryDto
import com.poshanforlife.android.core.network.ReportListItemDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

sealed class PatientListUiState {
    data object Loading : PatientListUiState()
    data class Success(val patients: List<PatientSummaryDto>) : PatientListUiState()
    data class Error(val message: String) : PatientListUiState()
}

sealed class PatientDetailUiState {
    data object Loading : PatientDetailUiState()
    data class Success(val patient: PatientDetailDto) : PatientDetailUiState()
    data class Error(val message: String) : PatientDetailUiState()
}

sealed class PatientReportsUiState {
    data object Loading : PatientReportsUiState()
    data class Success(val reports: List<ReportListItemDto>) : PatientReportsUiState()
    data class Error(val message: String) : PatientReportsUiState()
}

sealed class PatientProgrammesUiState {
    data object Loading : PatientProgrammesUiState()
    data class Success(val programmes: List<PatientProgrammeDto>) : PatientProgrammesUiState()
    data class Error(val message: String) : PatientProgrammesUiState()
}

sealed class NotesSaveState {
    data object Idle : NotesSaveState()
    data object Saving : NotesSaveState()
    data object Saved : NotesSaveState()
    data class Error(val message: String) : NotesSaveState()
}

/**
 * Backs both PatientListScreen and PatientDetailScreen (a per-screen Hilt
 * instance each, same convention as AN-07's AppointmentsViewModel) — which
 * half of the state a screen reads depends on whether "patientId" is present
 * in its route (list has none, detail always does).
 */
@HiltViewModel
class PatientManagementViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientId: String? = savedStateHandle["patientId"]

    // ---- list ----------------------------------------------------------

    private val _listState = MutableStateFlow<PatientListUiState>(PatientListUiState.Loading)
    val listState: StateFlow<PatientListUiState> = _listState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ---- detail ----------------------------------------------------------

    private val _detailState = MutableStateFlow<PatientDetailUiState>(PatientDetailUiState.Loading)
    val detailState: StateFlow<PatientDetailUiState> = _detailState.asStateFlow()

    private val _reportsState = MutableStateFlow<PatientReportsUiState>(PatientReportsUiState.Loading)
    val reportsState: StateFlow<PatientReportsUiState> = _reportsState.asStateFlow()

    private val _programmesState = MutableStateFlow<PatientProgrammesUiState>(PatientProgrammesUiState.Loading)
    val programmesState: StateFlow<PatientProgrammesUiState> = _programmesState.asStateFlow()

    private val _notesText = MutableStateFlow("")
    val notesText: StateFlow<String> = _notesText.asStateFlow()

    private val _notesSaveState = MutableStateFlow<NotesSaveState>(NotesSaveState.Idle)
    val notesSaveState: StateFlow<NotesSaveState> = _notesSaveState.asStateFlow()

    init {
        if (patientId == null) refreshList() else loadDetail()
    }

    private var listJob: Job? = null

    /** Immediate — used for pull-to-refresh, where there's no reason to wait. */
    fun refreshList() {
        listJob?.cancel()
        listJob = viewModelScope.launch { performListLoad() }
    }

    /** Debounced + cancels any in-flight search, so a slow response to a stale keystroke can't clobber a newer one. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.update { query }
        listJob?.cancel()
        listJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performListLoad()
        }
    }

    private suspend fun performListLoad() {
        _isRefreshing.update { true }
        when (val result = patientRepository.listPatients(_searchQuery.value.ifBlank { null })) {
            is Result.Success -> _listState.update { PatientListUiState.Success(result.data) }
            is Result.Error -> _listState.update { PatientListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }

    fun loadDetail() {
        val id = checkNotNull(patientId)
        viewModelScope.launch {
            _detailState.update { PatientDetailUiState.Loading }
            when (val result = patientRepository.getPatientDetail(id)) {
                is Result.Success -> {
                    _detailState.update { PatientDetailUiState.Success(result.data) }
                    _notesText.update { result.data.doctorNotes.orEmpty() }
                }
                is Result.Error -> _detailState.update { PatientDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
        loadReports()
        loadProgrammes()
    }

    private fun loadReports() {
        val id = checkNotNull(patientId)
        viewModelScope.launch {
            _reportsState.update { PatientReportsUiState.Loading }
            when (val result = reportRepository.listForPatient(id)) {
                is Result.Success -> _reportsState.update { PatientReportsUiState.Success(result.data) }
                is Result.Error -> _reportsState.update { PatientReportsUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** AN-19: ADMIN-only, enforced server-side too. Refreshes the list on success so the deleted row disappears. */
    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            when (reportRepository.deleteReport(reportId)) {
                is Result.Success -> loadReports()
                is Result.Error -> Unit
                Result.Loading -> Unit
            }
        }
    }

    private fun loadProgrammes() {
        val id = checkNotNull(patientId)
        viewModelScope.launch {
            _programmesState.update { PatientProgrammesUiState.Loading }
            when (val result = patientRepository.getProgrammes(id)) {
                is Result.Success -> _programmesState.update { PatientProgrammesUiState.Success(result.data) }
                is Result.Error -> _programmesState.update { PatientProgrammesUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onNotesChange(text: String) {
        _notesText.update { text }
        // Editing after a save/error invalidates that indicator — back to Idle until the next save.
        _notesSaveState.update { NotesSaveState.Idle }
    }

    fun saveNotes() {
        val id = checkNotNull(patientId)
        viewModelScope.launch {
            _notesSaveState.update { NotesSaveState.Saving }
            when (val result = patientRepository.updateDoctorNotes(id, _notesText.value)) {
                is Result.Success -> _notesSaveState.update { NotesSaveState.Saved }
                is Result.Error -> _notesSaveState.update { NotesSaveState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
