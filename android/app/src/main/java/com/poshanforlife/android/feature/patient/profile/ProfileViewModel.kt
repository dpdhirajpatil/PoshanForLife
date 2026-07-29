package com.poshanforlife.android.feature.patient.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.datastore.HealthConnectSyncStateDataStore
import com.poshanforlife.android.core.healthconnect.HealthConnectManager
import com.poshanforlife.android.core.healthconnect.HealthConnectSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userName: String? = null,
    /** False when Health Connect isn't installed / the OS provider needs an update. */
    val isHealthConnectAvailable: Boolean = true,
    val isHealthConnectConnected: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val syncScheduler: HealthConnectSyncScheduler,
    private val syncStateDataStore: HealthConnectSyncStateDataStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val requiredPermissions: Set<String> get() = healthConnectManager.permissions
    fun permissionRequestContract() = healthConnectManager.permissionRequestContract()
    fun playStoreIntent() = healthConnectManager.playStoreIntent()
    fun healthConnectSettingsIntent() = healthConnectManager.healthConnectSettingsIntent()

    private val isConnected = MutableStateFlow(false)

    val uiState: StateFlow<ProfileUiState> = combine(
        isConnected,
        syncStateDataStore.lastSyncedAtMillis,
        authRepository.currentUser(),
    ) { connected, lastSynced, user ->
        ProfileUiState(
            userName = user?.name,
            isHealthConnectAvailable = healthConnectManager.isAvailable(),
            isHealthConnectConnected = connected,
            lastSyncedAtMillis = lastSynced,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        refreshConnectionState()
    }

    fun refreshConnectionState() {
        viewModelScope.launch { isConnected.value = healthConnectManager.hasAllPermissions() }
    }

    /** Called after the permission request launcher returns. */
    fun onPermissionResult(granted: Set<String>) {
        val allGranted = granted.containsAll(requiredPermissions)
        isConnected.value = allGranted
        if (allGranted) {
            syncScheduler.schedulePeriodic()
            syncScheduler.syncNow()
        }
    }

    /** Called after returning from the Health Connect settings deep link (used for both connect-more and disconnect). */
    fun onReturnedFromHealthConnectSettings() {
        viewModelScope.launch {
            val granted = healthConnectManager.hasAllPermissions()
            isConnected.value = granted
            if (!granted) syncScheduler.cancelPeriodic()
        }
    }

    fun syncNow() = syncScheduler.syncNow()

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
