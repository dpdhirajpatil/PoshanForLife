package com.poshanforlife.android.feature.patient.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.datastore.SeenBadgesDataStore
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BadgesUiState {
    data object Loading : BadgesUiState()
    data class Success(val badges: List<PatientBadgeStatusDto>) : BadgesUiState()
    data class Error(val message: String) : BadgesUiState()
}

@HiltViewModel
class BadgesViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val seenBadgesDataStore: SeenBadgesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BadgesUiState>(BadgesUiState.Loading)
    val uiState: StateFlow<BadgesUiState> = _uiState.asStateFlow()

    /** Non-null while a newly-earned badge's celebration should play; cleared once shown. */
    private val _celebratingBadge = MutableStateFlow<PatientBadgeStatusDto?>(null)
    val celebratingBadge: StateFlow<PatientBadgeStatusDto?> = _celebratingBadge.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { BadgesUiState.Loading }
            val profileResult = patientRepository.getMe()
            val patientId = (profileResult as? Result.Success)?.data?.id
            if (patientId == null) {
                val message = (profileResult as? Result.Error)?.message ?: "Couldn't load profile"
                _uiState.update { BadgesUiState.Error(message) }
                return@launch
            }

            when (val result = patientRepository.getBadges(patientId)) {
                is Result.Success -> {
                    _uiState.update { BadgesUiState.Success(result.data) }
                    checkForNewlyEarned(result.data)
                }
                is Result.Error -> _uiState.update { BadgesUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun checkForNewlyEarned(badges: List<PatientBadgeStatusDto>) {
        if (_celebratingBadge.value != null) return
        val seen = seenBadgesDataStore.seenBadgeIds.first()
        val newlyEarned = badges.firstOrNull { it.earned && it.id !in seen }
        if (newlyEarned != null) {
            _celebratingBadge.value = newlyEarned
            seenBadgesDataStore.markSeen(newlyEarned.id)
        }
    }

    fun consumeCelebration() {
        _celebratingBadge.value = null
    }
}
