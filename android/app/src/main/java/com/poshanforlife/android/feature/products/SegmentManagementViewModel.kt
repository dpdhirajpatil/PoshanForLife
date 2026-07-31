package com.poshanforlife.android.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ProductRepository
import com.poshanforlife.android.core.network.CreateProductSegmentRequest
import com.poshanforlife.android.core.network.ProductSegmentDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateProductSegmentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SegmentManagementUiState {
    data object Loading : SegmentManagementUiState()
    data class Success(val segments: List<ProductSegmentDto>) : SegmentManagementUiState()
    data class Error(val message: String) : SegmentManagementUiState()
}

/**
 * "Archive" in this screen calls the segment DELETE endpoint — the backend
 * has no separate soft-archive guard (PATCH status=archived isn't blocked
 * by product count), only DELETE is, so that's the endpoint that actually
 * implements the prompt's "block if it still has products" requirement.
 */
@HiltViewModel
class SegmentManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SegmentManagementUiState>(SegmentManagementUiState.Loading)
    val uiState: StateFlow<SegmentManagementUiState> = _uiState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { SegmentManagementUiState.Loading }
            when (val result = productRepository.listSegments(includeArchived = true)) {
                is Result.Success -> _uiState.update { SegmentManagementUiState.Success(result.data) }
                is Result.Error -> _uiState.update { SegmentManagementUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun addSegment(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = productRepository.createSegment(CreateProductSegmentRequest(name = name))) {
                is Result.Success -> load()
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    fun renameSegment(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = productRepository.updateSegment(id, UpdateProductSegmentRequest(name = name))) {
                is Result.Success -> load()
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    /** Swaps displayOrder with the segment immediately before/after it in the current (already displayOrder-sorted) list. */
    fun moveSegment(id: String, direction: Int) {
        val segments = (_uiState.value as? SegmentManagementUiState.Success)?.segments ?: return
        val index = segments.indexOfFirst { it.id == id }
        val targetIndex = index + direction
        if (index < 0 || targetIndex < 0 || targetIndex >= segments.size) return
        val current = segments[index]
        val target = segments[targetIndex]
        viewModelScope.launch {
            productRepository.updateSegment(current.id, UpdateProductSegmentRequest(displayOrder = target.displayOrder))
            productRepository.updateSegment(target.id, UpdateProductSegmentRequest(displayOrder = current.displayOrder))
            load()
        }
    }

    fun archiveSegment(id: String) {
        viewModelScope.launch {
            when (val result = productRepository.deleteSegment(id)) {
                is Result.Success -> load()
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    fun consumeActionError() {
        _actionError.update { null }
    }
}
