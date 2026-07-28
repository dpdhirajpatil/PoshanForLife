package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DocumentApi {
    /**
     * status has no "pending" value server-side (draft|sent|paid) — "sent"
     * (issued, not yet paid) is the closest match for an outstanding-balance
     * card and is what patient-dashboard callers should pass.
     */
    @GET("api/v1/documents")
    suspend fun list(
        @Query("patientId") patientId: String? = null,
        @Query("leadId") leadId: String? = null,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
    ): Response<ApiResponse<List<DocumentListItemDto>>>

    @GET("api/v1/documents/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<DocumentDetailDto>>

    @POST("api/v1/documents")
    suspend fun create(@Body request: CreateDocumentRequest): Response<ApiResponse<DocumentDetailDto>>

    @POST("api/v1/documents/from-order")
    suspend fun fromOrder(@Body request: FromOrderRequest): Response<ApiResponse<DocumentDetailDto>>

    @PATCH("api/v1/documents/{id}")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body request: UpdateDocumentStatusRequest,
    ): Response<ApiResponse<DocumentDetailDto>>

    @GET("api/v1/documents/{id}/pdf")
    suspend fun getPdfUrl(@Path("id") id: String): Response<ApiResponse<PdfUrlDto>>
}
