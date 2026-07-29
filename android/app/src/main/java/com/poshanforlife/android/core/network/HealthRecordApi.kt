package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Body for {@code POST /api/v1/health-records}. patientId is omitted — a
 * PATIENT caller always writes their own record (see backend's
 * HealthRecordService#resolveTargetPatientId); source must be
 * "patient_manual" or "wearable_sync" for a PATIENT caller.
 */
@Serializable
data class UpsertHealthRecordRequest(
    val source: String,
    val weightKg: Double? = null,
)

/** `record` (the full HealthRecordDto) is present too but unused here — ignored via ignoreUnknownKeys. */
@Serializable
data class UpsertHealthRecordResponseDto(val upserted: Boolean = false)

interface HealthRecordApi {
    @POST("api/v1/health-records")
    suspend fun upsert(@Body request: UpsertHealthRecordRequest): Response<ApiResponse<UpsertHealthRecordResponseDto>>
}
