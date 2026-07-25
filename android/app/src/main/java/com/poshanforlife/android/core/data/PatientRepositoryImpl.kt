package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.DocumentApi
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientApi
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.Result
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

    override suspend fun getPendingInvoices(patientId: String): Result<List<DocumentListItemDto>> =
        safeApiCall(json) {
            documentApi.list(patientId = patientId, type = "invoice", status = "sent", limit = 10)
        }
}
