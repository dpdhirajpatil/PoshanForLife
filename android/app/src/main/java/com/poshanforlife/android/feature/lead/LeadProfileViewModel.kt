package com.poshanforlife.android.feature.lead

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.data.UserRepository
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeadProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val user: StateFlow<UserDto?> = authRepository.currentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** From GET /users/me — the cached UserDto has no phone field. Null unless OTP-verified. */
    private val _verifiedPhone = MutableStateFlow<String?>(null)
    val verifiedPhone: StateFlow<String?> = _verifiedPhone

    init {
        refreshVerifiedPhone()
    }

    fun refreshVerifiedPhone() {
        viewModelScope.launch {
            val result = userRepository.getMe()
            if (result is Result.Success) {
                _verifiedPhone.value = result.data.phone?.takeIf { result.data.phoneVerified }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
