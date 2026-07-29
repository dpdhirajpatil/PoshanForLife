package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LeadApi {
    /** Only used for the create-estimate picker today — see LeadPickerItemDto. */
    @GET("api/v1/leads")
    suspend fun list(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
    ): Response<ApiResponse<LeadListResponseDto>>

    /** Same endpoint as [list], parsed into the full CRM row+summary shape for LeadListScreen. */
    @GET("api/v1/leads")
    suspend fun listPipeline(
        @Query("stage") stage: String? = null,
        @Query("search") search: String? = null,
        @Query("followupToday") followupToday: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): Response<ApiResponse<LeadPipelineResponseDto>>

    @GET("api/v1/leads/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<LeadDetailDto>>

    @PATCH("api/v1/leads/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateLeadRequest): Response<ApiResponse<LeadDetailDto>>

    @POST("api/v1/leads/{id}/activities")
    suspend fun addActivity(
        @Path("id") id: String,
        @Body request: CreateLeadActivityRequest,
    ): Response<ApiResponse<LeadActivityDto>>

    @POST("api/v1/leads/{id}/convert")
    suspend fun convert(
        @Path("id") id: String,
        @Body request: ConvertLeadRequest,
    ): Response<ApiResponse<ConvertLeadResponseDto>>

    @POST("api/v1/leads/{id}/schedule-followup")
    suspend fun scheduleFollowup(
        @Path("id") id: String,
        @Body request: ScheduleFollowupRequest,
    ): Response<ApiResponse<LeadDetailDto>>
}
