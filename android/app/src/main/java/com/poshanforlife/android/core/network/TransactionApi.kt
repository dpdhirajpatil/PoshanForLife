package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TransactionApi {
    /** userId ("practitioner" in the UI) is ADMIN-only — silently ignored server-side for a DOCTOR caller. */
    @GET("api/v1/transactions")
    suspend fun list(
        @Query("search") search: String? = null,
        @Query("userId") userId: String? = null,
        @Query("catalogue") catalogue: String? = null,
        @Query("paymentType") paymentType: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): Response<ApiResponse<TransactionListResponseDto>>

    @GET("api/v1/transactions/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<TransactionDetailDto>>
}
