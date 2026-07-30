package com.poshanforlife.android.feature.practitioner.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.CatalogueRepository
import com.poshanforlife.android.core.network.CatalogueItemDto
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CatalogueListUiState {
    data object Loading : CatalogueListUiState()
    data class Success(val items: List<CatalogueItemDto>) : CatalogueListUiState()
    data class Error(val message: String) : CatalogueListUiState()
}

/** Backs CatalogueScreen's browse grid/list for all three types — filtering by type/status/search happens server-side. */
@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val catalogueRepository: CatalogueRepository,
) : ViewModel() {

    private val _selectedType = MutableStateFlow(CatalogueType.PROGRAMME)
    val selectedType: StateFlow<CatalogueType> = _selectedType.asStateFlow()

    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _listState = MutableStateFlow<CatalogueListUiState>(CatalogueListUiState.Loading)
    val listState: StateFlow<CatalogueListUiState> = _listState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onTypeChange(type: CatalogueType) {
        _selectedType.update { type }
        refresh()
    }

    fun onStatusFilterChange(status: String?) {
        _statusFilter.update { status }
        refresh()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.update { query }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { performLoad() }
    }

    private suspend fun performLoad() {
        _isRefreshing.update { true }
        when (
            val result = catalogueRepository.list(
                type = selectedType.value.pathSegment,
                status = statusFilter.value,
                search = searchQuery.value.ifBlank { null },
            )
        ) {
            is Result.Success -> _listState.update { CatalogueListUiState.Success(result.data) }
            is Result.Error -> _listState.update { CatalogueListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }
}
