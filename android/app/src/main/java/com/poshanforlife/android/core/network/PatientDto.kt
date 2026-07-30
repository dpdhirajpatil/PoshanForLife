package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * There is no dedicated /health-records endpoint on the backend — health
 * records are nested inside the patient detail response, newest first. The
 * patient-self dashboard only reads id/healthRecords; AN-09's practitioner
 * PatientDetailScreen needs the rest of the profile, so the remaining
 * fields are modeled here too rather than duplicating a second DTO for the
 * same endpoint. reports is omitted — the backend's own field is always []
 * (reports come from a separate GET /reports?patientId= call instead).
 */
@Serializable
data class PatientDetailDto(
    val id: String,
    val name: String = "",
    val email: String? = null,
    val phone: String? = null,
    val dateOfBirth: String? = null,
    val isActive: Boolean = true,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val heightCm: Double? = null,
    val emergencyContact: String? = null,
    val medicalHistory: String? = null,
    /** UI label is "Notes"/"Practitioner notes" — wire/DB name stays doctorNotes. */
    val doctorNotes: String? = null,
    val assignedDoctors: List<UserRefDto> = emptyList(),
    val healthRecords: List<HealthRecordDto> = emptyList(),
)

/** Row shape for the practitioner's patient list (GET /patients, scoped server-side). Age is derived client-side. */
@Serializable
data class PatientSummaryDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val dateOfBirth: String? = null,
    val isActive: Boolean = true,
    val assignedDoctors: List<UserRefDto> = emptyList(),
    /** Null when the patient has no reports yet. */
    val lastReportDate: String? = null,
)

@Serializable
data class UpdateDoctorNotesRequest(val doctorNotes: String)

@Serializable
data class HealthRecordDto(
    val id: String,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val bmi: Double? = null,
)

/**
 * serviceType is the lowercase wire enum ("programme"/"session"/"challenge");
 * status is ("active"/"completed"/"cancelled"). Backend's full DTO also has
 * assignedBy/order/createdAt/updatedAt — omitted here, unused by any screen.
 */
@Serializable
data class PatientProgrammeDto(
    val id: String,
    val serviceType: String? = null,
    val catalogueItem: ServiceRefDto? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val priceInr: Double? = null,
    val status: String,
    val notes: String? = null,
    val assignedDoctor: UserRefDto? = null,
    /** The order auto-created alongside this assignment — null only if the DTO variant that omits it was used. */
    val order: AssignmentOrderDto? = null,
)

/** AN-20: POST /patients/{patientId}/programmes body. serviceType is the wire singular label ("programme"/"session"/"challenge"); exactly the id matching it should be set. */
@Serializable
data class CreatePatientProgrammeRequest(
    val serviceType: String,
    val programmeId: String? = null,
    val sessionId: String? = null,
    val challengeId: String? = null,
    val startDate: String? = null,
    val priceInr: Double? = null,
    val notes: String? = null,
)

/** Minimal view of the order auto-created by assigning a service — mirrors backend's OrderSummaryDto (transactions omitted, unused by the assign-service receipt). */
@Serializable
data class AssignmentOrderDto(
    val id: String,
    val amountInr: Double,
    val status: String,
    val paymentStatus: String,
)

/** Duration fields are only ever non-null for the matching type (programme=weeks, session=minutes, challenge=days). */
@Serializable
data class ServiceRefDto(
    val id: String,
    val name: String,
    val serviceCode: String? = null,
    val durationWeeks: Int? = null,
    val durationMinutes: Int? = null,
    val durationDays: Int? = null,
)

@Serializable
data class UserRefDto(val id: String, val name: String)
