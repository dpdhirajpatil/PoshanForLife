package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ReportListResponseDto(
    val reports: List<ReportListItemDto> = emptyList(),
    val stats: ReportStatsDto? = null,
)

@Serializable
data class ReportStatsDto(
    val total: Long = 0,
    val pending: Long = 0,
    val processing: Long = 0,
    val done: Long = 0,
    val error: Long = 0,
    val thisMonth: Long = 0,
)

@Serializable
data class ReportListItemDto(
    val id: String,
    val patient: PatientRefDto? = null,
    val title: String,
    val type: String,
    val status: String,
    val createdAt: String,
    val createdBy: UserRefDto? = null,
)

@Serializable
data class ReportDetailDto(
    val id: String,
    val patient: PatientRefDto? = null,
    val title: String,
    val type: String,
    val notes: String? = null,
    val status: String,
    val fileUrl: String? = null,
    val parsedData: InBodyDataDto? = null,
    val confidence: String? = null,
    val extractedFieldCount: Int? = null,
    val extractionMethod: String? = null,
    val createdBy: UserRefDto? = null,
    val createdAt: String,
)

@Serializable
data class PatientRefDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
)

/** Mirrors backend's InBodyData record: 20 nullable fields, no nested groups server-side. */
@Serializable
data class InBodyDataDto(
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val skeletalMuscleMassKg: Double? = null,
    val bmi: Double? = null,
    val visceralFatLevel: Double? = null,
    val bodyWaterL: Double? = null,
    val proteinKg: Double? = null,
    val mineralKg: Double? = null,
    val basalMetabolicRate: Double? = null,
    val bodyFatMassKg: Double? = null,
    val fatFreeMassKg: Double? = null,
    val waistHipRatio: Double? = null,
    val targetWeightKg: Double? = null,
    val weightControlKg: Double? = null,
    val fatControlKg: Double? = null,
    val muscleControlKg: Double? = null,
    val obesityDegreePercent: Double? = null,
    val intracellularWaterL: Double? = null,
    val extracellularWaterL: Double? = null,
    val inbodyScore: Int? = null,
)
