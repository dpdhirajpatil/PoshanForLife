package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * Minimal fields needed for the lead-convert flow's catalogue picker — not
 * the full admin catalogue-management shape (no coverImageUrl/status/
 * activeAssignmentCount/createdBy). This is deliberately ahead of a real
 * AN-15 catalogue-management prompt; reshape rather than duplicate when
 * that lands.
 */
@Serializable
data class CataloguePickerItemDto(
    val id: String,
    val name: String,
    val priceInr: Double? = null,
    val durationWeeks: Int? = null,
    val durationMinutes: Int? = null,
    val durationDays: Int? = null,
)
