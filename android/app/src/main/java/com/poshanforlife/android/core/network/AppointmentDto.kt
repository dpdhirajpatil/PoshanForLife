package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * status is the lowercase wire enum ("scheduled"/"completed"/"cancelled").
 *
 * [videoRoomId] stays null until a video provider is actually integrated — AN-13
 * builds everything around the call except the call itself, so nothing populates
 * this field yet on either side.
 */
@Serializable
data class AppointmentDto(
    val id: String,
    val patient: UserRefDto,
    val practitioner: UserRefDto,
    val scheduledAt: String,
    val durationMinutes: Int,
    val status: String,
    val notes: String? = null,
    val isVideo: Boolean = false,
    val videoRoomId: String? = null,
    val createdAt: String,
)

/** time is "HH:mm:ss" (java.time.LocalTime's default ISO string). */
@Serializable
data class AvailableSlotDto(
    val time: String,
    val available: Boolean,
)

/** patientId is omitted by a PATIENT caller — the backend force-scopes to their own id. */
@Serializable
data class CreateAppointmentRequest(
    val patientId: String? = null,
    val practitionerId: String,
    val scheduledAt: String,
    val durationMinutes: Int? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateAppointmentRequest(
    val scheduledAt: String? = null,
    val status: String? = null,
    val notes: String? = null,
)
