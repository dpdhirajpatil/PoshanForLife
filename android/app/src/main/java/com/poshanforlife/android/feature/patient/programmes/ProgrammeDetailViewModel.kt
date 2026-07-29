package com.poshanforlife.android.feature.patient.programmes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.network.ChallengeProgressDto
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

sealed class ProgrammeDetailUiState {
    data object Loading : ProgrammeDetailUiState()
    data class Success(
        val programme: PatientProgrammeDto,
        val challengeProgress: ChallengeProgressDto? = null,
        val checkingIn: Boolean = false,
    ) : ProgrammeDetailUiState()
    data class Error(val message: String) : ProgrammeDetailUiState()
}

@HiltViewModel
class ProgrammeDetailViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientId: String = checkNotNull(savedStateHandle["patientId"])
    private val programmeId: String = checkNotNull(savedStateHandle["programmeId"])

    private val _uiState = MutableStateFlow<ProgrammeDetailUiState>(ProgrammeDetailUiState.Loading)
    val uiState: StateFlow<ProgrammeDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { ProgrammeDetailUiState.Loading }
            when (val result = patientRepository.getProgramme(patientId, programmeId)) {
                is Result.Success -> {
                    val programme = result.data
                    _uiState.update { ProgrammeDetailUiState.Success(programme) }
                    if (programme.serviceType == "challenge") {
                        loadChallengeProgress()
                    }
                }
                is Result.Error -> _uiState.update { ProgrammeDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun loadChallengeProgress() {
        when (val result = patientRepository.getChallengeProgress(patientId, programmeId)) {
            is Result.Success -> _uiState.update {
                (it as? ProgrammeDetailUiState.Success)?.copy(challengeProgress = result.data) ?: it
            }
            is Result.Error, Result.Loading -> Unit
        }
    }

    fun checkInToday() {
        val current = _uiState.value as? ProgrammeDetailUiState.Success ?: return
        if (current.challengeProgress?.lastLoggedDate == LocalDate.now().toString()) return
        viewModelScope.launch {
            _uiState.update { (it as? ProgrammeDetailUiState.Success)?.copy(checkingIn = true) ?: it }
            when (val result = patientRepository.checkIn(patientId, programmeId)) {
                is Result.Success -> _uiState.update {
                    (it as? ProgrammeDetailUiState.Success)
                        ?.copy(challengeProgress = result.data, checkingIn = false) ?: it
                }
                is Result.Error -> _uiState.update {
                    (it as? ProgrammeDetailUiState.Success)?.copy(checkingIn = false) ?: it
                }
                Result.Loading -> Unit
            }
        }
    }
}
