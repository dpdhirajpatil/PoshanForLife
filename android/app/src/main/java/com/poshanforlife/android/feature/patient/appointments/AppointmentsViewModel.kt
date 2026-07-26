package com.poshanforlife.android.feature.patient.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AppointmentRepository
import com.poshanforlife.android.core.network.AppointmentDto
import com.poshanforlife.android.core.network.AvailableSlotDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserRefDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class AppointmentsUiState {
    data object Loading : AppointmentsUiState()
    data class Success(val appointments: List<AppointmentDto>) : AppointmentsUiState()
    data class Error(val message: String) : AppointmentsUiState()
}

sealed class SlotsUiState {
    data object Idle : SlotsUiState()
    data object Loading : SlotsUiState()
    data class Success(val slots: List<AvailableSlotDto>) : SlotsUiState()
    data class Error(val message: String) : SlotsUiState()
}

sealed class BookingState {
    data object Idle : BookingState()
    data object InProgress : BookingState()
    data class Success(val appointmentId: String) : BookingState()
    data class Error(val message: String) : BookingState()
}

@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppointmentsUiState>(AppointmentsUiState.Loading)
    val uiState: StateFlow<AppointmentsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _practitioners = MutableStateFlow<List<UserRefDto>>(emptyList())
    val practitioners: StateFlow<List<UserRefDto>> = _practitioners.asStateFlow()

    private val _slotsUiState = MutableStateFlow<SlotsUiState>(SlotsUiState.Idle)
    val slotsUiState: StateFlow<SlotsUiState> = _slotsUiState.asStateFlow()

    private val _bookingState = MutableStateFlow<BookingState>(BookingState.Idle)
    val bookingState: StateFlow<BookingState> = _bookingState.asStateFlow()

    init {
        refresh()
    }

    fun loadPractitioners() {
        viewModelScope.launch {
            when (val result = appointmentRepository.myPractitioners()) {
                is Result.Success -> _practitioners.update { result.data }
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    fun loadSlots(practitionerId: String, date: LocalDate) {
        viewModelScope.launch {
            _slotsUiState.update { SlotsUiState.Loading }
            when (val result = appointmentRepository.availableSlots(practitionerId, date.toString())) {
                is Result.Success -> _slotsUiState.update { SlotsUiState.Success(result.data) }
                is Result.Error -> _slotsUiState.update { SlotsUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** rescheduleAppointmentId null = book a new appointment; non-null = reschedule that one instead. */
    fun confirmBooking(practitionerId: String, scheduledAtIso: String, rescheduleAppointmentId: String?) {
        viewModelScope.launch {
            _bookingState.update { BookingState.InProgress }
            val result = if (rescheduleAppointmentId != null) {
                appointmentRepository.reschedule(rescheduleAppointmentId, scheduledAtIso)
            } else {
                appointmentRepository.book(practitionerId, scheduledAtIso)
            }
            when (result) {
                is Result.Success -> _bookingState.update { BookingState.Success(result.data.id) }
                is Result.Error -> _bookingState.update { BookingState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun resetBookingState() {
        _bookingState.update { BookingState.Idle }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = appointmentRepository.list()) {
                is Result.Success -> _uiState.update { AppointmentsUiState.Success(result.data) }
                is Result.Error -> _uiState.update { AppointmentsUiState.Error(result.message) }
                Result.Loading -> Unit
            }
            _isRefreshing.value = false
        }
    }

    fun reschedule(id: String, scheduledAtIso: String) {
        viewModelScope.launch {
            when (val result = appointmentRepository.reschedule(id, scheduledAtIso)) {
                is Result.Success -> refresh()
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            when (val result = appointmentRepository.cancel(id)) {
                is Result.Success -> refresh()
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    fun clearActionError() {
        _actionError.update { null }
    }
}
