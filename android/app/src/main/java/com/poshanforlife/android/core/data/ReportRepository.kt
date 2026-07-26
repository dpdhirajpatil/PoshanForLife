package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.ReportListItemDto
import com.poshanforlife.android.core.network.ReportUploadResponseDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateReportRequest
import java.io.File

interface ReportRepository {
    suspend fun listInBodyReports(search: String? = null): Result<List<ReportListItemDto>>
    suspend fun getReport(id: String): Result<ReportDetailDto>

    /** All report types for one patient — the practitioner's patient-detail Reports tab. */
    suspend fun listForPatient(patientId: String): Result<List<ReportListItemDto>>

    /** AN-10: uploads a captured InBody report photo for AI extraction. */
    suspend fun uploadReport(patientId: String, file: File): Result<ReportUploadResponseDto>

    /** AN-10: confirms/corrects the extracted data (and/or title/notes) after review. */
    suspend fun updateReport(id: String, request: UpdateReportRequest): Result<ReportDetailDto>
}
