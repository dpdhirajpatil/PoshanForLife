package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * There is no dedicated /health-records endpoint on the backend — health
 * records are nested inside the patient detail response, newest first.
 * Only the subset PatientDetailDto/HealthRecordDto model here is what the
 * dashboard needs; both types have many more fields server-side.
 */
@Serializable
data class PatientDetailDto(
    val id: String,
    val healthRecords: List<HealthRecordDto> = emptyList(),
)

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
)

@Serializable
data class ServiceRefDto(val id: String, val name: String)

@Serializable
data class UserRefDto(val id: String, val name: String)
