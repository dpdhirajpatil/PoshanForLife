package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

/** OkHttp requires a request body for PATCH; the backend's markAllRead reads none, so this is just a placeholder. */
@Serializable
object EmptyBody

interface NotificationApi {
    @GET("api/v1/notifications")
    suspend fun list(
        @Query("limit") limit: Int = 50,
        @Query("unread") unread: Boolean = false,
    ): Response<ApiResponse<NotificationListResponseDto>>

    @PATCH("api/v1/notifications")
    suspend fun markAllRead(@Body body: EmptyBody = EmptyBody): Response<ApiResponse<Map<String, Boolean>>>
}
