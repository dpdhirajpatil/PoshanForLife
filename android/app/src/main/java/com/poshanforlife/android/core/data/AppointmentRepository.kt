package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.AppointmentDto
import com.poshanforlife.android.core.network.AvailableSlotDto
import com.poshanforlife.android.core.network.CreateAppointmentRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateAppointmentRequest
import com.poshanforlife.android.core.network.UserRefDto

interface AppointmentRepository {
    /** Force-scoped server-side to the caller's own appointments (patient or practitioner). */
    suspend fun list(status: String? = null, dateFrom: String? = null, dateTo: String? = null): Result<List<AppointmentDto>>

    suspend fun myPractitioners(): Result<List<UserRefDto>>

    suspend fun availableSlots(practitionerId: String, date: String): Result<List<AvailableSlotDto>>

    suspend fun book(
        practitionerId: String,
        scheduledAt: String,
        durationMinutes: Int? = null,
        notes: String? = null,
    ): Result<AppointmentDto>

    suspend fun reschedule(id: String, scheduledAt: String): Result<AppointmentDto>

    suspend fun cancel(id: String): Result<AppointmentDto>

    suspend fun complete(id: String, notes: String? = null): Result<AppointmentDto>

    /** Practitioner's post-appointment notes without changing status. */
    suspend fun updateNotes(id: String, notes: String): Result<AppointmentDto>
}
