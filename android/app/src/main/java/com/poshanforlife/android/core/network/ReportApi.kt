package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ReportApi {
    /** type defaults to "inbody" for the patient dashboard's existing callers; pass null for AN-09's "all types for one patient" list. */
    @GET("api/v1/reports")
    suspend fun list(
        @Query("type") type: String? = "inbody",
        @Query("search") search: String? = null,
        @Query("patientId") patientId: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<ReportListResponseDto>>

    @GET("api/v1/reports/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<ReportDetailDto>>
}
