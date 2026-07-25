package com.poshanforlife.android.feature.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Per-card loading state — one card's Error/Loading never masks another's Success. */
sealed class CardState<out T> {
    data object Loading : CardState<Nothing>()
    data class Success<T>(val data: T) : CardState<T>()
    data class Error(val message: String) : CardState<Nothing>()
}

data class DashboardUiState(
    val profile: CardState<UserDetailDto> = CardState.Loading,
    val healthRecord: CardState<HealthRecordDto?> = CardState.Loading,
    val programme: CardState<PatientProgrammeDto?> = CardState.Loading,
    val invoices: CardState<List<DocumentListItemDto>> = CardState.Loading,
    val isRefreshing: Boolean = false,
)

/**
 * There's no single "the dashboard load" network call to combine() in the
 * kotlinx.coroutines.flow sense — each card's data comes from a one-shot
 * suspend Retrofit call, not a stream. This gets the same outcome the prompt
 * asks for (independent per-card state, one slow/failed endpoint never blocks
 * or fails the others) by launching the three patient-scoped calls
 * concurrently and writing each into its own slice of one StateFlow.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    profile = CardState.Loading,
                    healthRecord = CardState.Loading,
                    programme = CardState.Loading,
                    invoices = CardState.Loading,
                )
            }

            // patientId (== caller's own id here) gates the other three calls, so profile loads first.
            val profileResult = patientRepository.getMe()
            _uiState.update { it.copy(profile = profileResult.toCardState()) }

            val patientId = (profileResult as? Result.Success)?.data?.id
            if (patientId == null) {
                val message = (profileResult as? Result.Error)?.message ?: "Couldn't load profile"
                _uiState.update {
                    it.copy(
                        healthRecord = CardState.Error(message),
                        programme = CardState.Error(message),
                        invoices = CardState.Error(message),
                        isRefreshing = false,
                    )
                }
                return@launch
            }

            val healthJob = launch {
                val result = patientRepository.getLatestHealthRecord(patientId)
                _uiState.update { it.copy(healthRecord = result.toCardState()) }
            }
            val programmeJob = launch {
                val result = patientRepository.getActiveProgramme(patientId)
                _uiState.update { it.copy(programme = result.toCardState()) }
            }
            val invoicesJob = launch {
                val result = patientRepository.getPendingInvoices(patientId)
                _uiState.update { it.copy(invoices = result.toCardState()) }
            }
            joinAll(healthJob, programmeJob, invoicesJob)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}

private fun <T> Result<T>.toCardState(): CardState<T> = when (this) {
    is Result.Success -> CardState.Success(data)
    is Result.Error -> CardState.Error(message)
    Result.Loading -> CardState.Loading
}
