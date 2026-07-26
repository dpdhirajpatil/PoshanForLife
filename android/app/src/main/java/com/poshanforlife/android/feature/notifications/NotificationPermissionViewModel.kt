package com.poshanforlife.android.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.datastore.TokenDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    private val tokenDataStore: TokenDataStore,
) : ViewModel() {

    /** Defaults to true (i.e. "already asked") until the real value loads, so the rationale dialog never flashes on cold start. */
    val alreadyAsked: StateFlow<Boolean> = tokenDataStore.notificationPermissionAsked()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun markAsked() {
        viewModelScope.launch { tokenDataStore.setNotificationPermissionAsked() }
    }
}
