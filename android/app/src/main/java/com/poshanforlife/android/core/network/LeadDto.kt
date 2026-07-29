package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/** Minimal fields needed for the create-estimate lead picker — not the full CRM lead shape. */
@Serializable
data class LeadPickerItemDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class LeadListResponseDto(
    val leads: List<LeadPickerItemDto>,
)

/**
 * The full CRM lead row shape (GET /leads), distinct from LeadPickerItemDto's
 * minimal fields used by the create-estimate picker. stage/source are the
 * lowercase wire enums.
 */
@Serializable
data class LeadListItemDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val city: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val source: String? = null,
    val stage: String,
    val assignedPractitioner: UserRefDto? = null,
    val interestedProgramme: ServiceRefDto? = null,
    val nextFollowupAt: String? = null,
    val createdAt: String? = null,
)

/**
 * stageCounts/followupToday/newThisWeek/converted/conversionRate always
 * reflect the search/practitionerId/source filters but never the list's own
 * stage filter — see the backend's LeadSummaryDto kdoc.
 */
@Serializable
data class LeadSummaryDto(
    val stageCounts: Map<String, Long> = emptyMap(),
    val followupToday: Long = 0,
    val newThisWeek: Long = 0,
    val converted: Long = 0,
    val conversionRate: Double = 0.0,
)

/** The full list endpoint's `data` payload — rows for the current page + the pipeline summary. */
@Serializable
data class LeadPipelineResponseDto(
    val leads: List<LeadListItemDto> = emptyList(),
    val summary: LeadSummaryDto? = null,
)

@Serializable
data class LeadActivityDto(
    val id: String,
    val activityType: String,
    val description: String? = null,
    val oldStage: String? = null,
    val newStage: String? = null,
    val createdBy: UserRefDto? = null,
    val createdAt: String? = null,
)

@Serializable
data class LeadDetailDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val city: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val source: String? = null,
    val healthGoal: String? = null,
    val notes: String? = null,
    val stage: String,
    val assignedPractitioner: UserRefDto? = null,
    val interestedProgramme: ServiceRefDto? = null,
    val nextFollowupAt: String? = null,
    val convertedPatientId: String? = null,
    val createdBy: UserRefDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val activities: List<LeadActivityDto> = emptyList(),
)

/** Only stage is ever PATCHed from the Android client today — the detail screen's dropdown. */
@Serializable
data class UpdateLeadRequest(val stage: String)

@Serializable
data class CreateLeadActivityRequest(val activityType: String, val description: String)

/**
 * All contact/medical fields are optional overrides of the lead's own data.
 * assignServiceId/serviceType/startDate/price are an optional immediate
 * catalogue assignment — omit them to convert without one. serviceType is
 * the singular wire label ("programme"/"session"/"challenge"), NOT the
 * plural catalogue path segment used by CatalogueApi's URL.
 */
@Serializable
data class ConvertLeadRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val dateOfBirth: String? = null,
    val bloodGroup: String? = null,
    val heightCm: Double? = null,
    val assignServiceId: String? = null,
    val serviceType: String? = null,
    val startDate: String? = null,
    val price: Double? = null,
)

@Serializable
data class ConvertLeadResponseDto(val patientId: String, val message: String? = null)

@Serializable
data class ScheduleFollowupRequest(val followupAt: String, val message: String? = null)
