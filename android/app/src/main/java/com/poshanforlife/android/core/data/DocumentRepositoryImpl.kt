package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CreateDocumentRequest
import com.poshanforlife.android.core.network.DocumentApi
import com.poshanforlife.android.core.network.DocumentDetailDto
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.FromOrderRequest
import com.poshanforlife.android.core.network.LeadApi
import com.poshanforlife.android.core.network.LeadPickerItemDto
import com.poshanforlife.android.core.network.PdfUrlDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateDocumentStatusRequest
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val documentApi: DocumentApi,
    private val leadApi: LeadApi,
    private val json: Json,
) : DocumentRepository {

    override suspend fun list(
        patientId: String?,
        leadId: String?,
        type: String?,
        status: String?,
        page: Int,
        limit: Int,
    ): Result<List<DocumentListItemDto>> =
        safeApiCall(json) { documentApi.list(patientId, leadId, type, status, page, limit) }

    override suspend fun getDetail(id: String): Result<DocumentDetailDto> =
        safeApiCall(json) { documentApi.get(id) }

    override suspend fun create(request: CreateDocumentRequest): Result<DocumentDetailDto> =
        safeApiCall(json) { documentApi.create(request) }

    override suspend fun fromOrder(orderId: String): Result<DocumentDetailDto> =
        safeApiCall(json) { documentApi.fromOrder(FromOrderRequest(orderId)) }

    override suspend fun updateStatus(id: String, status: String): Result<DocumentDetailDto> =
        safeApiCall(json) { documentApi.updateStatus(id, UpdateDocumentStatusRequest(status)) }

    override suspend fun getPdfUrl(id: String): Result<PdfUrlDto> =
        safeApiCall(json) { documentApi.getPdfUrl(id) }

    override suspend fun searchLeads(search: String?): Result<List<LeadPickerItemDto>> {
        val result = safeApiCall(json) { leadApi.list(search = search) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.leads)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }
}
