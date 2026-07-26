package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface PatientApi {
    /** Scoped server-side to the caller's own assigned patients for a DOCTOR. */
    @GET("api/v1/patients")
    suspend fun list(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<List<PatientSummaryDto>>>

    /** Also used for its nested healthRecords[0] on the patient dashboard — see PatientDetailDto's kdoc. */
    @GET("api/v1/patients/{id}")
    suspend fun getPatient(@Path("id") id: String): Response<ApiResponse<PatientDetailDto>>

    @PATCH("api/v1/patients/{id}")
    suspend fun updateDoctorNotes(
        @Path("id") id: String,
        @Body request: UpdateDoctorNotesRequest,
    ): Response<ApiResponse<PatientDetailDto>>

    /** No status/limit query support server-side — filter+pick client-side (see PatientRepositoryImpl). */
    @GET("api/v1/patients/{patientId}/programmes")
    suspend fun getProgrammes(@Path("patientId") patientId: String): Response<ApiResponse<List<PatientProgrammeDto>>>

    @GET("api/v1/patients/{patientId}/programmes/{ppId}")
    suspend fun getProgramme(
        @Path("patientId") patientId: String,
        @Path("ppId") ppId: String,
    ): Response<ApiResponse<PatientProgrammeDto>>
}
