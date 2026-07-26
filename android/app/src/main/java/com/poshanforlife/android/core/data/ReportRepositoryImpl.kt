package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ReportApi
import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.ReportListItemDto
import com.poshanforlife.android.core.network.ReportUploadResponseDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateReportRequest
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val reportApi: ReportApi,
    private val json: Json,
) : ReportRepository {

    override suspend fun listInBodyReports(search: String?): Result<List<ReportListItemDto>> {
        val result = safeApiCall(json) { reportApi.list(search = search) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.reports)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getReport(id: String): Result<ReportDetailDto> =
        safeApiCall(json) { reportApi.get(id) }

    override suspend fun listForPatient(patientId: String): Result<List<ReportListItemDto>> {
        val result = safeApiCall(json) { reportApi.list(type = null, patientId = patientId, limit = 50) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.reports)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun uploadReport(patientId: String, file: File): Result<ReportUploadResponseDto> {
        val patientIdBody = patientId.toRequestBody("text/plain".toMediaType())
        // AN-10's ViewModel always hands this a single-page PDF wrapping the captured
        // photo (see wrapImageAsPdf) — the backend's upload endpoint only accepts
        // application/pdf.
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("application/pdf".toMediaType()),
        )
        return safeApiCall(json) { reportApi.upload(patientIdBody, filePart) }
    }

    override suspend fun updateReport(id: String, request: UpdateReportRequest): Result<ReportDetailDto> =
        safeApiCall(json) { reportApi.update(id, request) }
}
