package com.poshanforlife.android.core.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.Query

interface CatalogueApi {
    /** type is the plural URL path segment ("programmes"/"sessions"/"challenges"), not the singular wire label. */
    @GET("api/v1/catalogue/{type}")
    suspend fun list(
        @Path("type") type: String,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<List<CatalogueItemDto>>>

    @GET("api/v1/catalogue/{type}/{id}")
    suspend fun get(@Path("type") type: String, @Path("id") id: String): Response<ApiResponse<CatalogueItemDto>>

    /** ADMIN-only server-side. */
    @POST("api/v1/catalogue/{type}")
    suspend fun create(
        @Path("type") type: String,
        @Body request: CreateCatalogueItemRequest,
    ): Response<ApiResponse<CatalogueItemDto>>

    /** ADMIN-only server-side. */
    @PATCH("api/v1/catalogue/{type}/{id}")
    suspend fun update(
        @Path("type") type: String,
        @Path("id") id: String,
        @Body request: UpdateCatalogueItemRequest,
    ): Response<ApiResponse<CatalogueItemDto>>

    /** ADMIN-only server-side — same shared cover-image upload shape as the web app. */
    @Multipart
    @POST("api/v1/catalogue/{type}/upload")
    suspend fun uploadCoverImage(
        @Path("type") type: String,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<UploadCoverImageResponseDto>>
}
