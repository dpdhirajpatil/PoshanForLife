package com.poshanforlife.android.feature.practitioner.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.DocumentRepository
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.network.CreateDocumentItemRequest
import com.poshanforlife.android.core.network.CreateDocumentRequest
import com.poshanforlife.android.core.network.LeadPickerItemDto
import com.poshanforlife.android.core.network.PatientSummaryDto
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
private const val CGST_RATE = 0.025
private const val SGST_RATE = 0.025

enum class EstimateSubjectType { PATIENT, LEAD }

data class EstimateLineItemUi(
    val id: Long,
    val itemName: String = "",
    val hsnSac: String = "",
    val quantity: String = "1",
    val rate: String = "",
)

data class EstimatePreviewTotals(val subtotal: Double, val cgst: Double, val sgst: Double, val total: Double)

sealed class EstimateSaveState {
    data object Idle : EstimateSaveState()
    data object Saving : EstimateSaveState()
    data class Success(val documentId: String) : EstimateSaveState()
    data class Error(val message: String) : EstimateSaveState()
}

data class CreateEstimateUiState(
    val subjectType: EstimateSubjectType = EstimateSubjectType.PATIENT,
    val subjectSearch: String = "",
    val isSearching: Boolean = false,
    val patientResults: List<PatientSummaryDto> = emptyList(),
    val leadResults: List<LeadPickerItemDto> = emptyList(),
    val selectedSubjectId: String? = null,
    val selectedSubjectName: String? = null,
    val items: List<EstimateLineItemUi> = listOf(EstimateLineItemUi(id = 0)),
    val discountInr: String = "",
    val notes: String = "",
    val saveState: EstimateSaveState = EstimateSaveState.Idle,
) {
    val canSave: Boolean
        get() = selectedSubjectId != null &&
            items.any { it.itemName.isNotBlank() && (it.rate.toDoubleOrNull() ?: 0.0) > 0 }

    val previewTotals: EstimatePreviewTotals
        get() {
            val itemsTotal = items.sumOf { (it.quantity.toIntOrNull() ?: 0) * (it.rate.toDoubleOrNull() ?: 0.0) }
            val discount = discountInr.toDoubleOrNull() ?: 0.0
            val subtotal = (itemsTotal - discount).coerceAtLeast(0.0)
            val cgst = subtotal * CGST_RATE
            val sgst = subtotal * SGST_RATE
            return EstimatePreviewTotals(subtotal, cgst, sgst, subtotal + cgst + sgst)
        }
}

/** Patient/lead picker + line-item builder for a new estimate — always documentType="estimate" (see screen name). */
@HiltViewModel
class CreateEstimateViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEstimateUiState())
    val uiState: StateFlow<CreateEstimateUiState> = _uiState.asStateFlow()

    private var nextItemId = 1L
    private var searchJob: Job? = null

    fun onSubjectTypeChange(type: EstimateSubjectType) {
        _uiState.update {
            it.copy(
                subjectType = type,
                subjectSearch = "",
                patientResults = emptyList(),
                leadResults = emptyList(),
                selectedSubjectId = null,
                selectedSubjectName = null,
            )
        }
    }

    fun onSubjectSearchChange(query: String) {
        _uiState.update { it.copy(subjectSearch = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSubjectSearch()
        }
    }

    private suspend fun performSubjectSearch() {
        _uiState.update { it.copy(isSearching = true) }
        val state = _uiState.value
        when (state.subjectType) {
            EstimateSubjectType.PATIENT -> {
                val result = patientRepository.listPatients(state.subjectSearch.ifBlank { null })
                if (result is Result.Success) _uiState.update { it.copy(patientResults = result.data) }
            }
            EstimateSubjectType.LEAD -> {
                val result = documentRepository.searchLeads(state.subjectSearch.ifBlank { null })
                if (result is Result.Success) _uiState.update { it.copy(leadResults = result.data) }
            }
        }
        _uiState.update { it.copy(isSearching = false) }
    }

    fun selectSubject(id: String, name: String) {
        _uiState.update { it.copy(selectedSubjectId = id, selectedSubjectName = name) }
    }

    fun clearSubject() {
        _uiState.update { it.copy(selectedSubjectId = null, selectedSubjectName = null) }
    }

    fun addItem() {
        _uiState.update { it.copy(items = it.items + EstimateLineItemUi(id = nextItemId++)) }
    }

    fun removeItem(id: Long) {
        _uiState.update { state ->
            val updated = state.items.filterNot { it.id == id }
            state.copy(items = updated.ifEmpty { listOf(EstimateLineItemUi(id = nextItemId++)) })
        }
    }

    fun updateItem(id: Long, transform: (EstimateLineItemUi) -> EstimateLineItemUi) {
        _uiState.update { state -> state.copy(items = state.items.map { if (it.id == id) transform(it) else it }) }
    }

    fun onDiscountChange(value: String) {
        _uiState.update { it.copy(discountInr = value) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun saveDraft() = save(sendAfterSave = false)

    fun saveAndSend() = save(sendAfterSave = true)

    private fun save(sendAfterSave: Boolean) {
        val state = _uiState.value
        val subjectId = state.selectedSubjectId ?: return
        val items = state.items
            .filter { it.itemName.isNotBlank() }
            .map {
                CreateDocumentItemRequest(
                    itemName = it.itemName,
                    hsnSac = it.hsnSac.ifBlank { null },
                    quantity = it.quantity.toIntOrNull() ?: 1,
                    rateInr = it.rate.toDoubleOrNull() ?: 0.0,
                )
            }
        if (items.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(saveState = EstimateSaveState.Saving) }
            val request = CreateDocumentRequest(
                documentType = "estimate",
                patientId = if (state.subjectType == EstimateSubjectType.PATIENT) subjectId else null,
                leadId = if (state.subjectType == EstimateSubjectType.LEAD) subjectId else null,
                items = items,
                notes = state.notes.ifBlank { null },
                discountInr = state.discountInr.toDoubleOrNull(),
            )
            when (val result = documentRepository.create(request)) {
                is Result.Success -> finishSave(result.data.id, sendAfterSave)
                is Result.Error -> _uiState.update { it.copy(saveState = EstimateSaveState.Error(result.message)) }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun finishSave(documentId: String, sendAfterSave: Boolean) {
        if (!sendAfterSave) {
            _uiState.update { it.copy(saveState = EstimateSaveState.Success(documentId)) }
            return
        }
        when (val result = documentRepository.updateStatus(documentId, "sent")) {
            is Result.Success -> _uiState.update { it.copy(saveState = EstimateSaveState.Success(result.data.id)) }
            is Result.Error -> _uiState.update { it.copy(saveState = EstimateSaveState.Error(result.message)) }
            Result.Loading -> Unit
        }
    }
}
