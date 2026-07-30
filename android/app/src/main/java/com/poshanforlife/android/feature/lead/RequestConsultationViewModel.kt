package com.poshanforlife.android.feature.lead

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.LeadSelfRepository
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RequestConsultationState {
    data object Idle : RequestConsultationState()
    data object Submitting : RequestConsultationState()
    data object Submitted : RequestConsultationState()
    data class Error(val message: String) : RequestConsultationState()
}

@HiltViewModel
class RequestConsultationViewModel @Inject constructor(
    private val leadSelfRepository: LeadSelfRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RequestConsultationState>(RequestConsultationState.Idle)
    val state: StateFlow<RequestConsultationState> = _state.asStateFlow()

    fun submit(preferredContactTime: String, message: String) {
        _state.update { RequestConsultationState.Submitting }
        viewModelScope.launch {
            val result = leadSelfRepository.requestConsultation(
                preferredContactTime.ifBlank { null },
                message.ifBlank { null },
            )
            _state.update {
                when (result) {
                    is Result.Success -> RequestConsultationState.Submitted
                    is Result.Error -> RequestConsultationState.Error(result.message)
                    Result.Loading -> it
                }
            }
        }
    }
}
