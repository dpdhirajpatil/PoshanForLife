package com.poshanforlife.android.feature.practitioner.upload

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.network.InBodyDataDto
import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateReportRequest
import com.poshanforlife.android.core.utils.wrapImageAsPdf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class ConfidenceTier { HIGH, MEDIUM, LOW }

/** Thresholds scaled to this app's real 20-field schema — the original Expo/web spec's ">30/15-30/<15" was tuned for a much larger field set that doesn't exist in this backend. */
fun confidenceTierFor(extractedFieldCount: Int): ConfidenceTier = when {
    extractedFieldCount > (REPORT_FIELD_COUNT * 0.7) -> ConfidenceTier.HIGH
    extractedFieldCount >= (REPORT_FIELD_COUNT * 0.35) -> ConfidenceTier.MEDIUM
    else -> ConfidenceTier.LOW
}

sealed class CaptureUiState {
    data object Ready : CaptureUiState()
    data class Captured(val file: File) : CaptureUiState()
    data object Uploading : CaptureUiState()
    data class UploadError(val message: String) : CaptureUiState()
}

sealed class ReviewUiState {
    data object Loading : ReviewUiState()
    data object Success : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}

sealed class ConfirmState {
    data object Idle : ConfirmState()
    data object Saving : ConfirmState()
    data object Saved : ConfirmState()
    data class Error(val message: String) : ConfirmState()
}

/**
 * Backs both CaptureScreen (no "reportId" in its route — "patientId" instead)
 * and ReviewScreen ("reportId" present) — same dual-purpose-ViewModel
 * convention as AN-07's AppointmentsViewModel / AN-09's
 * PatientManagementViewModel.
 */
@HiltViewModel
class ReportUploadViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientId: String? = savedStateHandle["patientId"]
    private val reportId: String? = savedStateHandle["reportId"]

    // ---- capture ----------------------------------------------------------

    private val _captureState = MutableStateFlow<CaptureUiState>(CaptureUiState.Ready)
    val captureState: StateFlow<CaptureUiState> = _captureState.asStateFlow()

    /** One-shot: set once upload+extraction succeeds, so the screen can navigate to ReviewScreen. */
    private val _uploadedReportId = MutableStateFlow<String?>(null)
    val uploadedReportId: StateFlow<String?> = _uploadedReportId.asStateFlow()

    /** Survives the Uploading/UploadError states (unlike CaptureUiState.Captured's file) so a failed upload can be retried without re-capturing. */
    private var pendingFile: File? = null

    fun onCaptured(file: File) {
        pendingFile = file
        _captureState.update { CaptureUiState.Captured(file) }
    }

    fun retake() {
        pendingFile = null
        _captureState.update { CaptureUiState.Ready }
    }

    fun usePhoto() {
        val file = pendingFile ?: return
        val id = checkNotNull(patientId)
        viewModelScope.launch {
            _captureState.update { CaptureUiState.Uploading }
            // Backend's upload endpoint only accepts application/pdf (its extraction
            // pipeline is PDFBox-based) — wrap the captured JPEG as a single-page PDF
            // rather than requiring a backend change to that shared pipeline.
            val pdfFile = withContext(Dispatchers.IO) { wrapImageAsPdf(file, context.cacheDir) }
            when (val result = reportRepository.uploadReport(id, pdfFile)) {
                is Result.Success -> _uploadedReportId.update { result.data.reportId }
                is Result.Error -> _captureState.update { CaptureUiState.UploadError(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun consumeUploadedEvent() {
        pendingFile = null
        _uploadedReportId.update { null }
        _captureState.update { CaptureUiState.Ready }
    }

    // ---- review -------------------------------------------------------

    private val _reviewState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val reviewState: StateFlow<ReviewUiState> = _reviewState.asStateFlow()

    private val _report = MutableStateFlow<ReportDetailDto?>(null)
    val report: StateFlow<ReportDetailDto?> = _report.asStateFlow()

    private val _editedData = MutableStateFlow(InBodyDataDto())
    val editedData: StateFlow<InBodyDataDto> = _editedData.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _confirmState = MutableStateFlow<ConfirmState>(ConfirmState.Idle)
    val confirmState: StateFlow<ConfirmState> = _confirmState.asStateFlow()

    init {
        if (reportId != null) loadReport()
    }

    private fun loadReport() {
        val id = checkNotNull(reportId)
        viewModelScope.launch {
            _reviewState.update { ReviewUiState.Loading }
            when (val result = reportRepository.getReport(id)) {
                is Result.Success -> {
                    _report.update { result.data }
                    _editedData.update { result.data.parsedData ?: InBodyDataDto() }
                    _title.update { result.data.title }
                    _notes.update { result.data.notes.orEmpty() }
                    _reviewState.update { ReviewUiState.Success }
                }
                is Result.Error -> _reviewState.update { ReviewUiState.Error(result.message) }
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

    fun onFieldChange(key: String, value: Double?) {
        _editedData.update { it.withValue(key, value) }
    }

    fun confirm() {
        val id = checkNotNull(reportId)
        viewModelScope.launch {
            _confirmState.update { ConfirmState.Saving }
            val request = UpdateReportRequest(
                title = _title.value,
                notes = _notes.value.ifBlank { null },
                parsedData = _editedData.value,
            )
            when (val result = reportRepository.updateReport(id, request)) {
                is Result.Success -> _confirmState.update { ConfirmState.Saved }
                is Result.Error -> _confirmState.update { ConfirmState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
