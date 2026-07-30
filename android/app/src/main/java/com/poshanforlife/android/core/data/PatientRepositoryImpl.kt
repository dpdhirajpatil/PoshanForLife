package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ChallengeProgressDto
import com.poshanforlife.android.core.network.CreatePatientProgrammeRequest
import com.poshanforlife.android.core.network.DocumentApi
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientApi
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.core.network.PatientDetailDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.PatientSummaryDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateDoctorNotesRequest
import com.poshanforlife.android.core.network.UserApi
import com.poshanforlife.android.core.network.UserDetailDto
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class PatientRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val patientApi: PatientApi,
    private val documentApi: DocumentApi,
    private val json: Json,
) : PatientRepository {

    override suspend fun getMe(): Result<UserDetailDto> =
        safeApiCall(json) { userApi.me() }

    override suspend fun listPatients(search: String?): Result<List<PatientSummaryDto>> =
        safeApiCall(json) { patientApi.list(search = search) }

    override suspend fun getPatientDetail(patientId: String): Result<PatientDetailDto> =
        safeApiCall(json) { patientApi.getPatient(patientId) }

    override suspend fun updateDoctorNotes(patientId: String, notes: String): Result<PatientDetailDto> =
        safeApiCall(json) { patientApi.updateDoctorNotes(patientId, UpdateDoctorNotesRequest(notes)) }

    override suspend fun getLatestHealthRecord(patientId: String): Result<HealthRecordDto?> {
        val result = safeApiCall(json) { patientApi.getPatient(patientId) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.healthRecords.firstOrNull())
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getActiveProgramme(patientId: String): Result<PatientProgrammeDto?> {
        val result = safeApiCall(json) { patientApi.getProgrammes(patientId) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.firstOrNull { it.status == "active" })
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getProgrammes(patientId: String): Result<List<PatientProgrammeDto>> =
        safeApiCall(json) { patientApi.getProgrammes(patientId) }

    override suspend fun getProgramme(patientId: String, ppId: String): Result<PatientProgrammeDto> =
        safeApiCall(json) { patientApi.getProgramme(patientId, ppId) }

    override suspend fun assignService(patientId: String, request: CreatePatientProgrammeRequest): Result<PatientProgrammeDto> =
        safeApiCall(json) { patientApi.createProgramme(patientId, request) }

    override suspend fun getPendingInvoices(patientId: String): Result<List<DocumentListItemDto>> =
        safeApiCall(json) {
            documentApi.list(patientId = patientId, type = "invoice", status = "sent", limit = 10)
        }

    override suspend fun getBadges(patientId: String): Result<List<PatientBadgeStatusDto>> =
        safeApiCall(json) { patientApi.getBadges(patientId) }

    override suspend fun getChallengeProgress(patientId: String, ppId: String): Result<ChallengeProgressDto> =
        safeApiCall(json) { patientApi.getChallengeProgress(patientId, ppId) }

    override suspend fun checkIn(patientId: String, ppId: String): Result<ChallengeProgressDto> =
        safeApiCall(json) { patientApi.checkIn(patientId, ppId) }
}
