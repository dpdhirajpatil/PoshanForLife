package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Phone-number OTP auth (backend feature 19). Paths are the backend's real
 * routes (/api/v1/auth/otp/...), not the shorter /api/auth/... the AN-23
 * prompt assumes — same correction already noted on [AuthApi].
 *
 * Both endpoints are public on the backend's auth path. For
 * ADD_PHONE the caller must be signed in, which the server checks itself; the
 * shared OkHttp AuthInterceptor attaches the bearer token automatically, so
 * there's nothing to pass explicitly here.
 */
interface OtpApi {
    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestBody): Response<ApiResponse<OtpRequestResponse>>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyBody): Response<ApiResponse<OtpVerifyResponse>>
}
