package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LeadApi {
    /** Only used for the create-estimate picker today — see LeadPickerItemDto. */
    @GET("api/v1/leads")
    suspend fun list(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
    ): Response<ApiResponse<LeadListResponseDto>>
}
