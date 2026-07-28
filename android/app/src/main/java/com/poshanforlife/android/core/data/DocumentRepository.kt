package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CreateDocumentRequest
import com.poshanforlife.android.core.network.DocumentDetailDto
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.FromOrderRequest
import com.poshanforlife.android.core.network.LeadPickerItemDto
import com.poshanforlife.android.core.network.PdfUrlDto
import com.poshanforlife.android.core.network.Result

/** Estimates & invoices for practitioners/admins — server-scoped to the caller's own leads/patients for a DOCTOR. */
interface DocumentRepository {
    suspend fun list(
        patientId: String? = null,
        leadId: String? = null,
        type: String? = null,
        status: String? = null,
        page: Int = 1,
        limit: Int = 20,
    ): Result<List<DocumentListItemDto>>

    suspend fun getDetail(id: String): Result<DocumentDetailDto>

    suspend fun create(request: CreateDocumentRequest): Result<DocumentDetailDto>

    suspend fun fromOrder(orderId: String): Result<DocumentDetailDto>

    suspend fun updateStatus(id: String, status: String): Result<DocumentDetailDto>

    suspend fun getPdfUrl(id: String): Result<PdfUrlDto>

    /** Patient/lead picker for the create-estimate flow — not a full CRM lead search. */
    suspend fun searchLeads(search: String? = null): Result<List<LeadPickerItemDto>>
}
