package com.poshanforlife.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AppointmentApi {
    @GET("api/v1/appointments")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
    ): Response<ApiResponse<List<AppointmentDto>>>

    /** The caller's own assigned practitioners — the pool a PATIENT may book with. */
    @GET("api/v1/appointments/practitioners")
    suspend fun myPractitioners(): Response<ApiResponse<List<UserRefDto>>>

    @GET("api/v1/appointments/available-slots")
    suspend fun availableSlots(
        @Query("practitionerId") practitionerId: String,
        @Query("date") date: String,
    ): Response<ApiResponse<List<AvailableSlotDto>>>

    @POST("api/v1/appointments")
    suspend fun create(@Body request: CreateAppointmentRequest): Response<ApiResponse<AppointmentDto>>

    @PATCH("api/v1/appointments/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: UpdateAppointmentRequest,
    ): Response<ApiResponse<AppointmentDto>>
}
