package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    /**
     * Chronological ascending (oldest first) — `limit` returns the most recent N but keeps
     * that order, so the result is already chart-ready without reversing. `fields` is a
     * comma-separated allowlist the backend uses to null out every other metric.
     */
    @GET("api/v1/health-records/{patientId}")
    suspend fun list(
        @Path("patientId") patientId: String,
        @Query("limit") limit: Int? = null,
        @Query("fields") fields: String? = null,
    ): Response<ApiResponse<List<HealthRecordDto>>>
}
