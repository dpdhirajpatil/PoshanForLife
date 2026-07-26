package com.poshanforlife.android.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.NotificationRepository
import com.poshanforlife.android.core.fcm.DeepLinkEvents
import com.poshanforlife.android.core.network.AppNotificationDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NotificationsUiState {
    data object Loading : NotificationsUiState()
    data class Success(val notifications: List<AppNotificationDto>, val unreadCount: Long) : NotificationsUiState()
    data class Error(val message: String) : NotificationsUiState()
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val deepLinkEvents: DeepLinkEvents,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = notificationRepository.list()) {
                is Result.Success -> _uiState.update {
                    NotificationsUiState.Success(result.data.notifications, result.data.unreadCount)
                }
                is Result.Error -> _uiState.update { NotificationsUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** Called when the bell dropdown/sheet is dismissed — the backend only supports "mark all read". */
    fun markAllRead() {
        viewModelScope.launch {
            when (notificationRepository.markAllRead()) {
                is Result.Success -> refresh()
                else -> Unit
            }
        }
    }

    /** Same relatedEntityType/relatedEntityId deep-link mechanism the FCM push payload uses. */
    fun open(relatedEntityType: String?, relatedEntityId: String?) {
        deepLinkEvents.emit(relatedEntityType, relatedEntityId)
    }
}
