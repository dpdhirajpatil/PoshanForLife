package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Self-service endpoints for a mobile self-signup account (role LEAD) — kept separate from LeadApi, which is staff-facing CRM. */
interface LeadSelfApi {
    @POST("api/v1/leads/me/request-consultation")
    suspend fun requestConsultation(
        @Body body: RequestConsultationRequest,
    ): Response<ApiResponse<Map<String, Boolean>>>
}

/** Both fields optional — appended as a NOTE activity on the caller's own Lead record server-side. */
@Serializable
data class RequestConsultationRequest(
    val preferredContactTime: String? = null,
    val message: String? = null,
)
