package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderApi {
    @GET("api/v1/orders")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("paymentStatus") paymentStatus: String? = null,
        @Query("search") search: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): Response<ApiResponse<List<OrderListItemDto>>>

    @GET("api/v1/orders/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<OrderDetailDto>>

    @PATCH("api/v1/orders/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: UpdateOrderRequest,
    ): Response<ApiResponse<OrderDetailDto>>
}
