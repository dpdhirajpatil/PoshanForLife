package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

/** Self-service endpoints for a mobile self-signup account (role LEAD) — kept separate from LeadApi, which is staff-facing CRM. */
interface LeadSelfApi {
    @POST("api/v1/leads/me/request-consultation")
    suspend fun requestConsultation(
        @Body body: RequestConsultationRequest,
    ): Response<ApiResponse<Map<String, Boolean>>>

    /** AN-22: a lightweight, standalone streak (no PatientProgramme backing it, unlike PatientApi.getChallengeProgress). */
    @GET("api/v1/leads/me/streak")
    suspend fun getStreak(): Response<ApiResponse<LeadStreakDto>>

    @PATCH("api/v1/leads/me/streak/check-in")
    suspend fun checkInStreak(): Response<ApiResponse<LeadStreakDto>>

    /** Only STREAK_DAYS-criteria badges — see the backend LeadBadgeService's kdoc for why the rest are excluded. */
    @GET("api/v1/leads/me/badges")
    suspend fun getBadges(): Response<ApiResponse<List<PatientBadgeStatusDto>>>
}

/** Both fields optional — appended as a NOTE activity on the caller's own Lead record server-side. */
@Serializable
data class RequestConsultationRequest(
    val preferredContactTime: String? = null,
    val message: String? = null,
)

/** percentComplete treats a 7-day streak as "full" for the progress ring — see the backend LeadStreakDto's kdoc. */
@Serializable
data class LeadStreakDto(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastLoggedDate: String? = null,
    val percentComplete: Int,
)
