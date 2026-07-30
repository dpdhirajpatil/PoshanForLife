package com.poshanforlife.android.feature.patient.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.network.InBodyDataDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateReportRequest
import com.poshanforlife.android.feature.practitioner.upload.withValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The statuses a practitioner/admin can ever manually set — PENDING/PROCESSING are system-set by AN-10's upload pipeline. */
val EDITABLE_REPORT_STATUSES = listOf("done", "error")

sealed class EditReportUiState {
    data object Loading : EditReportUiState()
    data class Success(val type: String) : EditReportUiState()
    data class Error(val message: String) : EditReportUiState()
}

sealed class EditReportSaveState {
    data object Idle : EditReportSaveState()
    data object Saving : EditReportSaveState()
    data object Saved : EditReportSaveState()
    data class Error(val message: String) : EditReportSaveState()
}

@HiltViewModel
class EditReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val _uiState = MutableStateFlow<EditReportUiState>(EditReportUiState.Loading)
    val uiState: StateFlow<EditReportUiState> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _status = MutableStateFlow("done")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _editedData = MutableStateFlow(InBodyDataDto())
    val editedData: StateFlow<InBodyDataDto> = _editedData.asStateFlow()

    private val _saveState = MutableStateFlow<EditReportSaveState>(EditReportSaveState.Idle)
    val saveState: StateFlow<EditReportSaveState> = _saveState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { EditReportUiState.Loading }
            when (val result = reportRepository.getReport(reportId)) {
                is Result.Success -> {
                    val report = result.data
                    _title.update { report.title }
                    _notes.update { report.notes.orEmpty() }
                    _status.update { report.status }
                    _editedData.update { report.parsedData ?: InBodyDataDto() }
                    _uiState.update { EditReportUiState.Success(report.type) }
                }
                is Result.Error -> _uiState.update { EditReportUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onTitleChange(value: String) {
        _title.update { value }
    }

    fun onNotesChange(value: String) {
        _notes.update { value }
    }

    fun onStatusChange(value: String) {
        _status.update { value }
    }

    fun onFieldChange(key: String, value: Double?) {
        _editedData.update { it.withValue(key, value) }
    }

    fun save() {
        val isInbody = (uiState.value as? EditReportUiState.Success)?.type == "inbody"
        viewModelScope.launch {
            _saveState.update { EditReportSaveState.Saving }
            val request = UpdateReportRequest(
                title = _title.value,
                notes = _notes.value.ifBlank { null },
                status = _status.value,
                parsedData = if (isInbody) _editedData.value else null,
            )
            when (val result = reportRepository.updateReport(reportId, request)) {
                is Result.Success -> _saveState.update { EditReportSaveState.Saved }
                is Result.Error -> _saveState.update { EditReportSaveState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
