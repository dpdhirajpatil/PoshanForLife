package com.poshanforlife.android.feature.patient.programmes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProgrammesUiState {
    data object Loading : ProgrammesUiState()
    data class Success(val patientId: String, val programmes: List<PatientProgrammeDto>) : ProgrammesUiState()
    data class Error(val message: String) : ProgrammesUiState()
}

@HiltViewModel
class ProgrammesViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProgrammesUiState>(ProgrammesUiState.Loading)
    val uiState: StateFlow<ProgrammesUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true

            val profileResult = patientRepository.getMe()
            val patientId = (profileResult as? Result.Success)?.data?.id
            if (patientId == null) {
                val message = (profileResult as? Result.Error)?.message ?: "Couldn't load profile"
                _uiState.update { ProgrammesUiState.Error(message) }
                _isRefreshing.value = false
                return@launch
            }

            when (val result = patientRepository.getProgrammes(patientId)) {
                is Result.Success -> _uiState.update { ProgrammesUiState.Success(patientId, result.data) }
                is Result.Error -> _uiState.update { ProgrammesUiState.Error(result.message) }
                Result.Loading -> Unit
            }
            _isRefreshing.value = false
        }
    }
}
