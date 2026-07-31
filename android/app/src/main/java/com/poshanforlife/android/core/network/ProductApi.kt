package com.poshanforlife.android.core.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApi {
    @GET("api/v1/products/segments")
    suspend fun listSegments(
        @Query("includeArchived") includeArchived: Boolean = false,
    ): Response<ApiResponse<List<ProductSegmentDto>>>

    @POST("api/v1/products/segments")
    suspend fun createSegment(@Body request: CreateProductSegmentRequest): Response<ApiResponse<ProductSegmentDto>>

    @PATCH("api/v1/products/segments/{id}")
    suspend fun updateSegment(
        @Path("id") id: String,
        @Body request: UpdateProductSegmentRequest,
    ): Response<ApiResponse<ProductSegmentDto>>

    /** Hard delete, blocked server-side (with a clear error) if the segment still has any products. */
    @DELETE("api/v1/products/segments/{id}")
    suspend fun deleteSegment(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @GET("api/v1/products")
    suspend fun list(
        @Query("segmentId") segmentId: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<ProductListResponseDto>>

    @GET("api/v1/products/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<ProductDto>>

    @POST("api/v1/products")
    suspend fun create(@Body request: CreateProductRequest): Response<ApiResponse<ProductDto>>

    @PATCH("api/v1/products/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateProductRequest): Response<ApiResponse<ProductDto>>

    @DELETE("api/v1/products/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    /** Appends the uploaded image's URL to the product's images list, returning the updated product. */
    @Multipart
    @POST("api/v1/products/{id}/upload-image")
    suspend fun uploadImage(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<ProductDto>>

    @DELETE("api/v1/products/{id}/images")
    suspend fun removeImage(@Path("id") id: String, @Query("url") url: String): Response<ApiResponse<ProductDto>>
}
