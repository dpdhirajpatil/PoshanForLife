package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * The full badge catalog annotated per-patient (GET /patients/{id}/badges) —
 * includes locked/unearned badges so BadgesScreen can render them
 * grayed-out. criteriaType is the lowercase wire enum
 * ("challenge_completed"/"programme_count"/"streak_days"/"custom").
 */
@Serializable
data class PatientBadgeStatusDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val iconKey: String,
    val criteriaType: String,
    val criteriaValue: Int,
    val earned: Boolean,
    val earnedAt: String? = null,
)

/** One challenge-type PatientProgramme's check-in state — always server-computed, never trusted from the client. */
@Serializable
data class ChallengeProgressDto(
    val id: String,
    val patientProgrammeId: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastLoggedDate: String? = null,
    val percentComplete: Int,
    val updatedAt: String? = null,
)

@Serializable
data class CheckInRequest(val checkedInToday: Boolean = true)
