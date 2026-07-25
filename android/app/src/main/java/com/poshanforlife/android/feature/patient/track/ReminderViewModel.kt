package com.poshanforlife.android.feature.patient.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.MedicationReminderRepository
import com.poshanforlife.android.core.data.local.MedicationReminderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repository: MedicationReminderRepository,
) : ViewModel() {

    val reminders: StateFlow<List<MedicationReminderEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(label: String, timeOfDay: String, daysOfWeek: String) {
        viewModelScope.launch { repository.add(label, timeOfDay, daysOfWeek) }
    }

    fun setEnabled(reminder: MedicationReminderEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(reminder, enabled) }
    }

    fun delete(reminder: MedicationReminderEntity) {
        viewModelScope.launch { repository.delete(reminder) }
    }
}
