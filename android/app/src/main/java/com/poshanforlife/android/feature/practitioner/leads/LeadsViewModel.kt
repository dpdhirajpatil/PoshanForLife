package com.poshanforlife.android.feature.practitioner.leads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.LeadRepository
import com.poshanforlife.android.core.network.LeadDetailDto
import com.poshanforlife.android.core.network.LeadListItemDto
import com.poshanforlife.android.core.network.LeadSummaryDto
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

sealed class LeadListUiState {
    data object Loading : LeadListUiState()
    data class Success(val leads: List<LeadListItemDto>, val summary: LeadSummaryDto?) : LeadListUiState()
    data class Error(val message: String) : LeadListUiState()
}

sealed class LeadDetailUiState {
    data object Loading : LeadDetailUiState()
    data class Success(val lead: LeadDetailDto) : LeadDetailUiState()
    data class Error(val message: String) : LeadDetailUiState()
}

sealed class LeadActionState {
    data object Idle : LeadActionState()
    data object InFlight : LeadActionState()
    data class Error(val message: String) : LeadActionState()
}

/**
 * Backs LeadListScreen and LeadDetailScreen (a per-screen Hilt instance
 * each, same dual-purpose convention as DocumentsViewModel/
 * PatientManagementViewModel) — which half of the state a screen reads
 * depends on whether "leadId" is present in its route. Reused verbatim by
 * both the practitioner and admin nav graphs, same as Documents/Orders.
 */
@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val leadRepository: LeadRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val leadId: String? = savedStateHandle["leadId"]

    // ---- list ----------------------------------------------------------

    private val _listState = MutableStateFlow<LeadListUiState>(LeadListUiState.Loading)
    val listState: StateFlow<LeadListUiState> = _listState.asStateFlow()

    private val _stageFilter = MutableStateFlow<String?>(null)
    val stageFilter: StateFlow<String?> = _stageFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var listJob: Job? = null

    // ---- detail ----------------------------------------------------------

    private val _detailState = MutableStateFlow<LeadDetailUiState>(LeadDetailUiState.Loading)
    val detailState: StateFlow<LeadDetailUiState> = _detailState.asStateFlow()

    private val _stageChangeState = MutableStateFlow<LeadActionState>(LeadActionState.Idle)
    val stageChangeState: StateFlow<LeadActionState> = _stageChangeState.asStateFlow()

    private val _activityState = MutableStateFlow<LeadActionState>(LeadActionState.Idle)
    val activityState: StateFlow<LeadActionState> = _activityState.asStateFlow()

    private val _followupState = MutableStateFlow<LeadActionState>(LeadActionState.Idle)
    val followupState: StateFlow<LeadActionState> = _followupState.asStateFlow()

    init {
        if (leadId == null) refreshList() else loadDetail()
    }

    /** Immediate — used for pull-to-refresh and filter chip taps. */
    fun refreshList() {
        listJob?.cancel()
        listJob = viewModelScope.launch { performListLoad() }
    }

    fun onStageFilterChange(stage: String?) {
        _stageFilter.update { stage }
        refreshList()
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
        when (val result = leadRepository.listPipeline(stage = _stageFilter.value, search = _searchQuery.value.ifBlank { null })) {
            is Result.Success -> _listState.update { LeadListUiState.Success(result.data.leads, result.data.summary) }
            is Result.Error -> _listState.update { LeadListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }

    fun loadDetail() {
        val id = checkNotNull(leadId)
        viewModelScope.launch {
            _detailState.update { LeadDetailUiState.Loading }
            when (val result = leadRepository.getLead(id)) {
                is Result.Success -> _detailState.update { LeadDetailUiState.Success(result.data) }
                is Result.Error -> _detailState.update { LeadDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun changeStage(newStage: String) {
        val id = checkNotNull(leadId)
        viewModelScope.launch {
            _stageChangeState.update { LeadActionState.InFlight }
            when (val result = leadRepository.updateStage(id, newStage)) {
                is Result.Success -> {
                    _detailState.update { LeadDetailUiState.Success(result.data) }
                    _stageChangeState.update { LeadActionState.Idle }
                }
                is Result.Error -> _stageChangeState.update { LeadActionState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun logActivity(activityType: String, description: String) {
        val id = checkNotNull(leadId)
        viewModelScope.launch {
            _activityState.update { LeadActionState.InFlight }
            when (val result = leadRepository.addActivity(id, activityType, description)) {
                is Result.Success -> {
                    _activityState.update { LeadActionState.Idle }
                    loadDetail()
                }
                is Result.Error -> _activityState.update { LeadActionState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun scheduleFollowup(followupAt: String, message: String?) {
        val id = checkNotNull(leadId)
        viewModelScope.launch {
            _followupState.update { LeadActionState.InFlight }
            when (val result = leadRepository.scheduleFollowup(id, followupAt, message)) {
                is Result.Success -> {
                    _detailState.update { LeadDetailUiState.Success(result.data) }
                    _followupState.update { LeadActionState.Idle }
                }
                is Result.Error -> _followupState.update { LeadActionState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
