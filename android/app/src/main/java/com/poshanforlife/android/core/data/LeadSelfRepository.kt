package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.LeadStreakDto
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.core.network.Result

/** Self-service actions for a mobile self-signup account (role LEAD) — separate from LeadRepository's staff-facing CRM. */
interface LeadSelfRepository {
    suspend fun requestConsultation(preferredContactTime: String?, message: String?): Result<Unit>

    suspend fun getStreak(): Result<LeadStreakDto>

    suspend fun checkInStreak(): Result<LeadStreakDto>

    suspend fun getBadges(): Result<List<PatientBadgeStatusDto>>
}
