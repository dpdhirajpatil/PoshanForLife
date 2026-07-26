package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface UserApi {
    @GET("api/v1/users/me")
    suspend fun me(): Response<ApiResponse<UserDetailDto>>

    /** No PATCH /users/me on the backend — self-update goes through PATCH /users/{id}. */
    @PATCH("api/v1/users/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: UpdateUserRequest,
    ): Response<ApiResponse<UserDetailDto>>
}
