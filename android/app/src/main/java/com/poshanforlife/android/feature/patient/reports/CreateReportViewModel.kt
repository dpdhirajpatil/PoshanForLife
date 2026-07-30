package com.poshanforlife.android.feature.patient.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.network.CreateReportRequest
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** INBODY is deliberately excluded — that type only ever gets created via AN-10's camera+AI pipeline. */
val MANUAL_REPORT_TYPES = listOf("lab", "prescription", "other")

sealed class CreateReportSaveState {
    data object Idle : CreateReportSaveState()
    data object Saving : CreateReportSaveState()
    data class Saved(val reportId: String) : CreateReportSaveState()
    data class Error(val message: String) : CreateReportSaveState()
}

@HiltViewModel
class CreateReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientId: String = checkNotNull(savedStateHandle["patientId"])

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _type = MutableStateFlow(MANUAL_REPORT_TYPES.first())
    val type: StateFlow<String> = _type.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _saveState = MutableStateFlow<CreateReportSaveState>(CreateReportSaveState.Idle)
    val saveState: StateFlow<CreateReportSaveState> = _saveState.asStateFlow()

    fun onTitleChange(value: String) {
        _title.update { value }
    }

    fun onTypeChange(value: String) {
        _type.update { value }
    }

    fun onNotesChange(value: String) {
        _notes.update { value }
    }

    fun save() {
        val request = CreateReportRequest(
            patientId = patientId,
            title = _title.value,
            type = _type.value,
            notes = _notes.value.ifBlank { null },
        )
        viewModelScope.launch {
            _saveState.update { CreateReportSaveState.Saving }
            when (val result = reportRepository.createReport(request)) {
                is Result.Success -> _saveState.update { CreateReportSaveState.Saved(result.data.id) }
                is Result.Error -> _saveState.update { CreateReportSaveState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
