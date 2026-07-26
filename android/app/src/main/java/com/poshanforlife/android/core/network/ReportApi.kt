package com.poshanforlife.android.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
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

    /** AN-10: multipart field names ("patientId", "file") are the literal wire contract, matching the web app and backend prompt 09 exactly. */
    @Multipart
    @POST("api/v1/reports/upload")
    suspend fun upload(
        @Part("patientId") patientId: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<ReportUploadResponseDto>>

    @PATCH("api/v1/reports/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateReportRequest,
    ): Response<ApiResponse<ReportDetailDto>>
}
