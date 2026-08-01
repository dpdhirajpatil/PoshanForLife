package com.poshanforlife.android.feature.patient.appointments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AppointmentRepository
import com.poshanforlife.android.core.network.AppointmentDto
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs AN-13's pre-call and call screens, which only need the appointment's
 * practitioner name. Resolved by filtering the caller's own appointment list —
 * the backend has no `GET /appointments/{id}`, and adding one for a name lookup
 * isn't worth a backend change while the call itself is still a scaffold.
 */
@HiltViewModel
class VideoCallViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val appointmentId: String = checkNotNull(savedStateHandle["appointmentId"])

    private val _appointment = MutableStateFlow<AppointmentDto?>(null)
    val appointment: StateFlow<AppointmentDto?> = _appointment.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = appointmentRepository.list()) {
                is Result.Success -> _appointment.update { result.data.firstOrNull { it.id == appointmentId } }
                is Result.Error, Result.Loading -> Unit
            }
        }
    }
}
