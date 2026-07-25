package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET

interface UserApi {
    @GET("api/v1/users/me")
    suspend fun me(): Response<ApiResponse<UserDetailDto>>
}
