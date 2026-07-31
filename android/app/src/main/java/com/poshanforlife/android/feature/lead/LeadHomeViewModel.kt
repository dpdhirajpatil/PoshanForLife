package com.poshanforlife.android.feature.lead

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.data.LeadSelfRepository
import com.poshanforlife.android.core.network.LeadStreakDto
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** AN-22: streak + badges for the gamified home screen, on top of AN-18's existing greeting-only shape. */
data class LeadHomeGamificationState(
    val loading: Boolean = true,
    val streak: LeadStreakDto? = null,
    val badges: List<PatientBadgeStatusDto> = emptyList(),
    val checkingIn: Boolean = false,
)

@HiltViewModel
class LeadHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val leadSelfRepository: LeadSelfRepository,
) : ViewModel() {

    val userName: StateFlow<String> = authRepository.currentUser()
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _gamification = MutableStateFlow(LeadHomeGamificationState())
    val gamification: StateFlow<LeadHomeGamificationState> = _gamification.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val streakResult = leadSelfRepository.getStreak()
            val badgesResult = leadSelfRepository.getBadges()
            _gamification.update {
                it.copy(
                    loading = false,
                    streak = (streakResult as? Result.Success)?.data ?: it.streak,
                    badges = (badgesResult as? Result.Success)?.data ?: it.badges,
                )
            }
        }
    }

    fun checkInToday() {
        val alreadyCheckedIn = _gamification.value.streak?.lastLoggedDate == LocalDate.now().toString()
        if (alreadyCheckedIn || _gamification.value.checkingIn) return

        _gamification.update { it.copy(checkingIn = true) }
        viewModelScope.launch {
            when (val result = leadSelfRepository.checkInStreak()) {
                is Result.Success -> _gamification.update { it.copy(checkingIn = false, streak = result.data) }
                is Result.Error -> _gamification.update { it.copy(checkingIn = false) }
                Result.Loading -> Unit
            }
            // A badge may have just been newly earned server-side by the check-in — refresh the row.
            (leadSelfRepository.getBadges() as? Result.Success)?.let { badges ->
                _gamification.update { it.copy(badges = badges.data) }
            }
        }
    }
}
