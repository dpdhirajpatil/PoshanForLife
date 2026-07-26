package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.AppointmentApi
import com.poshanforlife.android.core.network.AppointmentDto
import com.poshanforlife.android.core.network.AvailableSlotDto
import com.poshanforlife.android.core.network.CreateAppointmentRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateAppointmentRequest
import com.poshanforlife.android.core.network.UserRefDto
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AppointmentRepositoryImpl @Inject constructor(
    private val appointmentApi: AppointmentApi,
    private val json: Json,
) : AppointmentRepository {

    override suspend fun list(status: String?, dateFrom: String?, dateTo: String?): Result<List<AppointmentDto>> =
        safeApiCall(json) { appointmentApi.list(status = status, dateFrom = dateFrom, dateTo = dateTo) }

    override suspend fun myPractitioners(): Result<List<UserRefDto>> =
        safeApiCall(json) { appointmentApi.myPractitioners() }

    override suspend fun availableSlots(practitionerId: String, date: String): Result<List<AvailableSlotDto>> =
        safeApiCall(json) { appointmentApi.availableSlots(practitionerId, date) }

    override suspend fun book(
        practitionerId: String,
        scheduledAt: String,
        durationMinutes: Int?,
        notes: String?,
    ): Result<AppointmentDto> = safeApiCall(json) {
        appointmentApi.create(
            CreateAppointmentRequest(
                practitionerId = practitionerId,
                scheduledAt = scheduledAt,
                durationMinutes = durationMinutes,
                notes = notes,
            ),
        )
    }

    override suspend fun reschedule(id: String, scheduledAt: String): Result<AppointmentDto> =
        safeApiCall(json) { appointmentApi.update(id, UpdateAppointmentRequest(scheduledAt = scheduledAt)) }

    override suspend fun cancel(id: String): Result<AppointmentDto> =
        safeApiCall(json) { appointmentApi.update(id, UpdateAppointmentRequest(status = "cancelled")) }

    override suspend fun complete(id: String, notes: String?): Result<AppointmentDto> =
        safeApiCall(json) { appointmentApi.update(id, UpdateAppointmentRequest(status = "completed", notes = notes)) }

    override suspend fun updateNotes(id: String, notes: String): Result<AppointmentDto> =
        safeApiCall(json) { appointmentApi.update(id, UpdateAppointmentRequest(notes = notes)) }
}
