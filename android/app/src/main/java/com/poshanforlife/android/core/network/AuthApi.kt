package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Paths are the backend's real routes (/api/v1/auth/...), not the shorter
 * /api/auth/... some prompts assume — AuthController is @RequestMapping
 * ("/api/v1/auth").
 */
interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<AuthResponse>>

    /** Public — creates a role=LEAD user + linked Lead record (source=mobile_app) in one transaction, server-side. */
    @POST("api/v1/auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<ApiResponse<AuthResponse>>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<ApiResponse<AuthResponse>>
}
