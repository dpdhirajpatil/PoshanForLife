package com.poshanforlife.android.feature.practitioner.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.DocumentRepository
import com.poshanforlife.android.core.network.DocumentDetailDto
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DocumentListUiState {
    data object Loading : DocumentListUiState()
    data class Success(val documents: List<DocumentListItemDto>) : DocumentListUiState()
    data class Error(val message: String) : DocumentListUiState()
}

sealed class DocumentDetailUiState {
    data object Loading : DocumentDetailUiState()
    data class Success(val document: DocumentDetailDto) : DocumentDetailUiState()
    data class Error(val message: String) : DocumentDetailUiState()
}

sealed class PdfShareState {
    data object Idle : PdfShareState()
    data object Loading : PdfShareState()
    data class Ready(val url: String) : PdfShareState()
    data class Error(val message: String) : PdfShareState()
}

/**
 * Backs both DocumentsListScreen and DocumentDetailScreen (a per-screen Hilt
 * instance each, same dual-purpose convention as PatientManagementViewModel) —
 * which half of the state a screen reads depends on whether "documentId" is
 * present in its route.
 */
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: String? = savedStateHandle["documentId"]

    // ---- list ----------------------------------------------------------

    private val _listState = MutableStateFlow<DocumentListUiState>(DocumentListUiState.Loading)
    val listState: StateFlow<DocumentListUiState> = _listState.asStateFlow()

    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var listJob: Job? = null

    // ---- detail ----------------------------------------------------------

    private val _detailState = MutableStateFlow<DocumentDetailUiState>(DocumentDetailUiState.Loading)
    val detailState: StateFlow<DocumentDetailUiState> = _detailState.asStateFlow()

    private val _pdfShareState = MutableStateFlow<PdfShareState>(PdfShareState.Idle)
    val pdfShareState: StateFlow<PdfShareState> = _pdfShareState.asStateFlow()

    init {
        if (documentId == null) refreshList() else loadDetail()
    }

    fun refreshList() {
        listJob?.cancel()
        listJob = viewModelScope.launch { performListLoad() }
    }

    fun onTypeFilterChange(type: String?) {
        _typeFilter.update { type }
        refreshList()
    }

    fun onStatusFilterChange(status: String?) {
        _statusFilter.update { status }
        refreshList()
    }

    private suspend fun performListLoad() {
        _isRefreshing.update { true }
        when (val result = documentRepository.list(type = _typeFilter.value, status = _statusFilter.value)) {
            is Result.Success -> _listState.update { DocumentListUiState.Success(result.data) }
            is Result.Error -> _listState.update { DocumentListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }

    fun loadDetail() {
        val id = checkNotNull(documentId)
        viewModelScope.launch {
            _detailState.update { DocumentDetailUiState.Loading }
            when (val result = documentRepository.getDetail(id)) {
                is Result.Success -> _detailState.update { DocumentDetailUiState.Success(result.data) }
                is Result.Error -> _detailState.update { DocumentDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** Renders (server-cached thereafter) and mints a fresh signed URL every call. */
    fun sharePdf() {
        val id = checkNotNull(documentId)
        viewModelScope.launch {
            _pdfShareState.update { PdfShareState.Loading }
            when (val result = documentRepository.getPdfUrl(id)) {
                is Result.Success -> _pdfShareState.update { PdfShareState.Ready(result.data.pdfUrl) }
                is Result.Error -> _pdfShareState.update { PdfShareState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** Called once the screen has launched the share sheet / shown the error, so it doesn't refire on recomposition. */
    fun consumePdfShareEvent() {
        _pdfShareState.update { PdfShareState.Idle }
    }
}
