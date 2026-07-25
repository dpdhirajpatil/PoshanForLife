package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DocumentApi {
    /**
     * status has no "pending" value server-side (draft|sent|paid) — "sent"
     * (issued, not yet paid) is the closest match for an outstanding-balance
     * card and is what callers should pass.
     */
    @GET("api/v1/documents")
    suspend fun list(
        @Query("patientId") patientId: String,
        @Query("type") type: String,
        @Query("status") status: String,
        @Query("limit") limit: Int,
    ): Response<ApiResponse<List<DocumentListItemDto>>>
}
