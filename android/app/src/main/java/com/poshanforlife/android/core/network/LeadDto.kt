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
