package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogueApi {
    /** type is the plural URL path segment ("programmes"/"sessions"/"challenges"), not the singular wire label. */
    @GET("api/v1/catalogue/{type}")
    suspend fun list(
        @Path("type") type: String,
        @Query("status") status: String? = "published",
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<List<CataloguePickerItemDto>>>
}
