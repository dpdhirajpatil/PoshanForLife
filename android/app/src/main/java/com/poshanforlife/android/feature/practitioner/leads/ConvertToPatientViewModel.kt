package com.poshanforlife.android.feature.practitioner.leads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.CatalogueRepository
import com.poshanforlife.android.core.data.LeadRepository
import com.poshanforlife.android.core.network.CatalogueItemDto
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.core.network.ConvertLeadRequest
import com.poshanforlife.android.core.network.LeadDetailDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class ConvertLeadUiState {
    data object Loading : ConvertLeadUiState()
    data class Error(val message: String) : ConvertLeadUiState()
    data class Form(
        val lead: LeadDetailDto,
        val name: String,
        val phone: String,
        val email: String,
        val bloodGroup: String = "",
        val heightCm: String = "",
        val assignService: Boolean = false,
        val serviceType: CatalogueType = CatalogueType.PROGRAMME,
        val serviceSearch: String = "",
        val serviceResults: List<CatalogueItemDto> = emptyList(),
        val selectedService: CatalogueItemDto? = null,
        val price: String = "",
        val startDate: LocalDate = LocalDate.now(),
        val submitting: Boolean = false,
        val submitError: String? = null,
    ) : ConvertLeadUiState()
}

@HiltViewModel
class ConvertToPatientViewModel @Inject constructor(
    private val leadRepository: LeadRepository,
    private val catalogueRepository: CatalogueRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val leadId: String = checkNotNull(savedStateHandle["leadId"])

    private val _uiState = MutableStateFlow<ConvertLeadUiState>(ConvertLeadUiState.Loading)
    val uiState: StateFlow<ConvertLeadUiState> = _uiState.asStateFlow()

    private val _convertedPatientId = MutableStateFlow<String?>(null)
    val convertedPatientId: StateFlow<String?> = _convertedPatientId.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { ConvertLeadUiState.Loading }
            when (val result = leadRepository.getLead(leadId)) {
                is Result.Success -> {
                    val lead = result.data
                    _uiState.update {
                        ConvertLeadUiState.Form(
                            lead = lead,
                            name = lead.name,
                            phone = lead.phone.orEmpty(),
                            email = lead.email.orEmpty(),
                        )
                    }
                }
                is Result.Error -> _uiState.update { ConvertLeadUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    private fun updateForm(transform: (ConvertLeadUiState.Form) -> ConvertLeadUiState.Form) {
        _uiState.update { (it as? ConvertLeadUiState.Form)?.let(transform) ?: it }
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value) }
    fun onPhoneChange(value: String) = updateForm { it.copy(phone = value) }
    fun onEmailChange(value: String) = updateForm { it.copy(email = value) }
    fun onBloodGroupChange(value: String) = updateForm { it.copy(bloodGroup = value) }
    fun onHeightChange(value: String) = updateForm { it.copy(heightCm = value) }
    fun onStartDateChange(value: LocalDate) = updateForm { it.copy(startDate = value) }
    fun onPriceChange(value: String) = updateForm { it.copy(price = value) }

    fun onAssignServiceToggle(assign: Boolean) {
        updateForm { it.copy(assignService = assign) }
        if (assign) searchCatalogue()
    }

    fun onServiceTypeChange(type: CatalogueType) {
        updateForm { it.copy(serviceType = type, selectedService = null, serviceResults = emptyList()) }
        searchCatalogue()
    }

    fun onServiceSearchChange(query: String) {
        updateForm { it.copy(serviceSearch = query) }
        searchCatalogue()
    }

    private fun searchCatalogue() {
        val form = _uiState.value as? ConvertLeadUiState.Form ?: return
        viewModelScope.launch {
            when (
                val result = catalogueRepository.list(
                    type = form.serviceType.pathSegment,
                    status = "published",
                    search = form.serviceSearch.ifBlank { null },
                )
            ) {
                is Result.Success -> updateForm { it.copy(serviceResults = result.data) }
                is Result.Error -> Unit
                Result.Loading -> Unit
            }
        }
    }

    fun onSelectService(item: CatalogueItemDto) {
        updateForm { it.copy(selectedService = item, price = item.priceInr?.toString() ?: it.price) }
    }

    fun clearSelectedService() {
        updateForm { it.copy(selectedService = null) }
        searchCatalogue()
    }

    fun submit() {
        val form = _uiState.value as? ConvertLeadUiState.Form ?: return
        viewModelScope.launch {
            updateForm { it.copy(submitting = true, submitError = null) }
            val request = ConvertLeadRequest(
                name = form.name.ifBlank { null },
                phone = form.phone.ifBlank { null },
                email = form.email.ifBlank { null },
                bloodGroup = form.bloodGroup.ifBlank { null },
                heightCm = form.heightCm.toDoubleOrNull(),
                assignServiceId = if (form.assignService) form.selectedService?.id else null,
                serviceType = if (form.assignService) form.selectedService?.let { form.serviceType.wireLabel } else null,
                startDate = if (form.assignService && form.selectedService != null) form.startDate.toString() else null,
                price = if (form.assignService) form.price.toDoubleOrNull() else null,
            )
            when (val result = leadRepository.convert(leadId, request)) {
                is Result.Success -> _convertedPatientId.update { result.data.patientId }
                is Result.Error -> updateForm { it.copy(submitting = false, submitError = result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
