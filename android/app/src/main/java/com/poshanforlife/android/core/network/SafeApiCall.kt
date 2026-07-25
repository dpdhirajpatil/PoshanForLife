package com.poshanforlife.android.core.network

import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Runs a Retrofit suspend call and converts it into [Result], parsing the
 * backend's ApiError envelope out of the error body on non-2xx responses.
 * Used by repository implementations so a network or serialization failure
 * never propagates as an uncaught exception into a ViewModel.
 */
suspend fun <T> safeApiCall(json: Json, apiCall: suspend () -> Response<ApiResponse<T>>): Result<T> {
    return try {
        val response = apiCall()
        val body = response.body()
        if (response.isSuccessful && body?.data != null) {
            Result.Success(body.data)
        } else {
            val apiError = response.errorBody()?.string()?.let {
                runCatching { json.decodeFromString(ApiError.serializer(), it) }.getOrNull()
            }
            Result.Error(
                code = apiError?.code ?: "HTTP_${response.code()}",
                message = apiError?.error ?: response.message(),
            )
        }
    } catch (e: IOException) {
        Result.Error(code = "NETWORK_ERROR", message = e.message ?: "Network error")
    } catch (e: Exception) {
        Result.Error(code = "UNKNOWN_ERROR", message = e.message ?: "Unknown error")
    }
}
