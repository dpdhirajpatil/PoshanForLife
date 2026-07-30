package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's CatalogueItemType. pathSegment is the URL segment
 * (/api/v1/catalogue/{pathSegment}, plural); wireLabel is the JSON value used
 * for a patient-programme serviceType field elsewhere (singular — e.g.
 * ConvertLeadRequest.serviceType in AN-14's convert flow, which is why this
 * enum lives in the shared network layer rather than the leads package that
 * originally defined a duplicate of it ahead of this prompt).
 */
enum class CatalogueType(val pathSegment: String, val wireLabel: String, val label: String) {
    PROGRAMME("programmes", "programme", "Programme"),
    SESSION("sessions", "session", "Session"),
    CHALLENGE("challenges", "challenge", "Challenge");

    companion object {
        fun fromPathSegment(segment: String): CatalogueType = entries.first { it.pathSegment == segment }
    }
}

/**
 * One DTO for all three catalogue types — the type-specific duration/goal
 * fields are null for the other types, same shape as the backend's
 * CatalogueItemDto. Superset of AN-14's original CataloguePickerItemDto
 * (kept id/name/priceInr/duration* field names identical so that code didn't
 * need to change).
 */
@Serializable
data class CatalogueItemDto(
    val id: String,
    val name: String,
    val serviceCode: String? = null,
    val type: String? = null,
    val priceInr: Double? = null,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val status: String = "draft",
    val durationWeeks: Int? = null,
    val durationMinutes: Int? = null,
    val durationDays: Int? = null,
    val goalDescription: String? = null,
    val activeAssignmentCount: Long = 0,
)

/** status defaults to draft server-side when omitted; only one of the three duration fields applies per type. */
@Serializable
data class CreateCatalogueItemRequest(
    val name: String,
    val serviceCode: String,
    val type: String,
    val priceInr: Double,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val status: String? = null,
    val durationWeeks: Int? = null,
    val durationMinutes: Int? = null,
    val durationDays: Int? = null,
    val goalDescription: String? = null,
)

/** Partial update — null fields are left unchanged server-side; "" clears description/coverImageUrl. */
@Serializable
data class UpdateCatalogueItemRequest(
    val name: String? = null,
    val serviceCode: String? = null,
    val priceInr: Double? = null,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val status: String? = null,
    val durationWeeks: Int? = null,
    val durationMinutes: Int? = null,
    val durationDays: Int? = null,
    val goalDescription: String? = null,
)

@Serializable
data class UploadCoverImageResponseDto(val url: String)
