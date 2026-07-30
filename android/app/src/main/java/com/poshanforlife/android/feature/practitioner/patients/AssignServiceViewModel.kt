package com.poshanforlife.android.feature.practitioner.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.CatalogueRepository
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.network.CatalogueItemDto
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.core.network.CreatePatientProgrammeRequest
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class AssignServiceUiState {
    data object Loading : AssignServiceUiState()
    data class Error(val message: String) : AssignServiceUiState()
    data class Form(
        val item: CatalogueItemDto,
        val type: CatalogueType,
        val patientName: String,
        val startDate: LocalDate = LocalDate.now(),
        val price: String,
        val notes: String = "",
        val submitting: Boolean = false,
        val submitError: String? = null,
    ) : AssignServiceUiState()
    data class Confirmed(val assignment: PatientProgrammeDto) : AssignServiceUiState()
}

/** AN-20: details+confirm step of the assign-service flow — reads type/itemId (picked in AN-15's CatalogueScreen picker mode) and patientId from the route. */
@HiltViewModel
class AssignServiceViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val catalogueRepository: CatalogueRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientId: String = checkNotNull(savedStateHandle["patientId"])
    private val type: CatalogueType = CatalogueType.fromPathSegment(checkNotNull(savedStateHandle["type"]))
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow<AssignServiceUiState>(AssignServiceUiState.Loading)
    val uiState: StateFlow<AssignServiceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { AssignServiceUiState.Loading }
            val itemResult = catalogueRepository.get(type.pathSegment, itemId)
            val patientResult = patientRepository.getPatientDetail(patientId)
            when {
                itemResult is Result.Success && patientResult is Result.Success -> {
                    _uiState.update {
                        AssignServiceUiState.Form(
                            item = itemResult.data,
                            type = type,
                            patientName = patientResult.data.name,
                            price = itemResult.data.priceInr?.let { formatPrice(it) }.orEmpty(),
                        )
                    }
                }
                itemResult is Result.Error -> _uiState.update { AssignServiceUiState.Error(itemResult.message) }
                patientResult is Result.Error -> _uiState.update { AssignServiceUiState.Error(patientResult.message) }
                else -> Unit
            }
        }
    }

    private fun updateForm(transform: (AssignServiceUiState.Form) -> AssignServiceUiState.Form) {
        _uiState.update { (it as? AssignServiceUiState.Form)?.let(transform) ?: it }
    }

    fun onStartDateChange(value: LocalDate) = updateForm { it.copy(startDate = value) }
    fun onPriceChange(value: String) = updateForm { it.copy(price = value) }
    fun onNotesChange(value: String) = updateForm { it.copy(notes = value) }

    fun confirm() {
        val form = _uiState.value as? AssignServiceUiState.Form ?: return
        viewModelScope.launch {
            updateForm { it.copy(submitting = true, submitError = null) }
            val request = CreatePatientProgrammeRequest(
                serviceType = form.type.wireLabel,
                programmeId = if (form.type == CatalogueType.PROGRAMME) form.item.id else null,
                sessionId = if (form.type == CatalogueType.SESSION) form.item.id else null,
                challengeId = if (form.type == CatalogueType.CHALLENGE) form.item.id else null,
                startDate = form.startDate.toString(),
                priceInr = form.price.toDoubleOrNull(),
                notes = form.notes.ifBlank { null },
            )
            when (val result = patientRepository.assignService(patientId, request)) {
                is Result.Success -> _uiState.update { AssignServiceUiState.Confirmed(result.data) }
                is Result.Error -> updateForm { it.copy(submitting = false, submitError = result.message) }
                Result.Loading -> Unit
            }
        }
    }
}

private fun formatPrice(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
