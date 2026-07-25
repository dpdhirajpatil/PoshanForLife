package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserDetailDto

interface PatientRepository {
    suspend fun getMe(): Result<UserDetailDto>

    /** Null data means "no health records yet" (not an error). */
    suspend fun getLatestHealthRecord(patientId: String): Result<HealthRecordDto?>

    /** Null data means "no active programme" (not an error). */
    suspend fun getActiveProgramme(patientId: String): Result<PatientProgrammeDto?>

    /** Empty list means "nothing outstanding" (not an error). */
    suspend fun getPendingInvoices(patientId: String): Result<List<DocumentListItemDto>>
}
