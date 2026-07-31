package com.poshanforlife.android.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ProductRepository
import com.poshanforlife.android.core.network.ProductDto
import com.poshanforlife.android.core.network.ProductSegmentDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SegmentsUiState {
    data object Loading : SegmentsUiState()
    data class Success(val segments: List<ProductSegmentDto>) : SegmentsUiState()
    data class Error(val message: String) : SegmentsUiState()
}

sealed class ProductsListUiState {
    data object Loading : ProductsListUiState()
    data class Success(val products: List<ProductDto>) : ProductsListUiState()
    data class Error(val message: String) : ProductsListUiState()
}

/**
 * Backs ProductsScreen for all four roles — segment tabs + a search-across-
 * all-segments field. While searchQuery is blank, the grid is scoped to
 * selectedSegmentId (one tab's products); once the user types a query, the
 * segment scope is dropped so results span every segment, per the prompt's
 * explicit "search field filtering across all segments" ask.
 */
@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _segmentsState = MutableStateFlow<SegmentsUiState>(SegmentsUiState.Loading)
    val segmentsState: StateFlow<SegmentsUiState> = _segmentsState.asStateFlow()

    private val _selectedSegmentId = MutableStateFlow<String?>(null)
    val selectedSegmentId: StateFlow<String?> = _selectedSegmentId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _listState = MutableStateFlow<ProductsListUiState>(ProductsListUiState.Loading)
    val listState: StateFlow<ProductsListUiState> = _listState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadSegments()
    }

    /** Re-fetches both segments (published counts change) and the current tab's products — used on pull-to-refresh and after returning from any admin mutation. */
    fun loadSegments() {
        viewModelScope.launch {
            when (val result = productRepository.listSegments()) {
                is Result.Success -> {
                    _segmentsState.update { SegmentsUiState.Success(result.data) }
                    if (_selectedSegmentId.value == null || result.data.none { it.id == _selectedSegmentId.value }) {
                        _selectedSegmentId.update { result.data.firstOrNull()?.id }
                    }
                    refresh()
                }
                is Result.Error -> _segmentsState.update { SegmentsUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onSegmentChange(segmentId: String) {
        _selectedSegmentId.update { segmentId }
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
        val query = _searchQuery.value.ifBlank { null }
        val segmentId = if (query == null) _selectedSegmentId.value else null
        when (val result = productRepository.list(segmentId = segmentId, search = query)) {
            is Result.Success -> _listState.update { ProductsListUiState.Success(result.data) }
            is Result.Error -> _listState.update { ProductsListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }

    fun deleteProduct(productId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (productRepository.delete(productId)) {
                is Result.Success -> {
                    refresh()
                    onDone()
                }
                is Result.Error -> onDone()
                Result.Loading -> Unit
            }
        }
    }
}
