package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ChallengeProgressDto
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.core.network.PatientDetailDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.PatientSummaryDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserDetailDto

interface PatientRepository {
    suspend fun getMe(): Result<UserDetailDto>

    /** Practitioner's own assigned patients (server-scoped for a DOCTOR caller). */
    suspend fun listPatients(search: String? = null): Result<List<PatientSummaryDto>>

    suspend fun getPatientDetail(patientId: String): Result<PatientDetailDto>

    suspend fun updateDoctorNotes(patientId: String, notes: String): Result<PatientDetailDto>

    /** Null data means "no health records yet" (not an error). */
    suspend fun getLatestHealthRecord(patientId: String): Result<HealthRecordDto?>

    /** Null data means "no active programme" (not an error). */
    suspend fun getActiveProgramme(patientId: String): Result<PatientProgrammeDto?>

    /** All of a patient's programme/session/challenge assignments, newest first. */
    suspend fun getProgrammes(patientId: String): Result<List<PatientProgrammeDto>>

    suspend fun getProgramme(patientId: String, ppId: String): Result<PatientProgrammeDto>

    /** Empty list means "nothing outstanding" (not an error). */
    suspend fun getPendingInvoices(patientId: String): Result<List<DocumentListItemDto>>

    /** Full badge catalog, earned + locked. */
    suspend fun getBadges(patientId: String): Result<List<PatientBadgeStatusDto>>

    suspend fun getChallengeProgress(patientId: String, ppId: String): Result<ChallengeProgressDto>

    /** Self check-in for today; a repeat call on the same day is a no-op server-side. */
    suspend fun checkIn(patientId: String, ppId: String): Result<ChallengeProgressDto>
}
