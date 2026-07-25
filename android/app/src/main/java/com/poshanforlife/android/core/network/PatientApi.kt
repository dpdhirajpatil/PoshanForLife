package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface PatientApi {
    /** Used only for its nested healthRecords[0] — see PatientDetailDto's kdoc. */
    @GET("api/v1/patients/{id}")
    suspend fun getPatient(@Path("id") id: String): Response<ApiResponse<PatientDetailDto>>

    /** No status/limit query support server-side — filter+pick client-side (see PatientRepositoryImpl). */
    @GET("api/v1/patients/{patientId}/programmes")
    suspend fun getProgrammes(@Path("patientId") patientId: String): Response<ApiResponse<List<PatientProgrammeDto>>>
}
