package com.poshanforlife.android.feature.lead

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Just the greeting name — cached, no network call needed (see AuthRepository.currentUser). */
@HiltViewModel
class LeadHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val userName: StateFlow<String> = authRepository.currentUser()
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
