package com.poshanforlife.android.feature.practitioner.schedule

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
import java.time.LocalDate
import javax.inject.Inject

sealed class ScheduleUiState {
    data object Loading : ScheduleUiState()
    data class Success(val appointments: List<AppointmentDto>) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        refresh()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.update { date }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val date = _selectedDate.value.toString()
            when (val result = appointmentRepository.list(dateFrom = date, dateTo = date)) {
                is Result.Success -> _uiState.update {
                    ScheduleUiState.Success(result.data.sortedBy { it.scheduledAt })
                }
                is Result.Error -> _uiState.update { ScheduleUiState.Error(result.message) }
                Result.Loading -> Unit
            }
            _isRefreshing.value = false
        }
    }

    fun markCompleted(id: String, notes: String?) {
        viewModelScope.launch {
            when (val result = appointmentRepository.complete(id, notes)) {
                is Result.Success -> refresh()
                is Result.Error -> _actionError.update { result.message }
                Result.Loading -> Unit
            }
        }
    }

    fun saveNotes(id: String, notes: String) {
        viewModelScope.launch {
            when (val result = appointmentRepository.updateNotes(id, notes)) {
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
