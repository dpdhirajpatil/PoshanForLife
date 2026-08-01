package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.HealthRecordApi
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpsertHealthRecordRequest
import com.poshanforlife.android.core.network.UpsertHealthRecordResponseDto
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class HealthRecordRepositoryImpl @Inject constructor(
    private val healthRecordApi: HealthRecordApi,
    private val json: Json,
) : HealthRecordRepository {

    override suspend fun upsert(source: String, weightKg: Double?): Result<UpsertHealthRecordResponseDto> =
        safeApiCall(json) { healthRecordApi.upsert(UpsertHealthRecordRequest(source = source, weightKg = weightKg)) }

    override suspend fun trends(patientId: String, limit: Int): Result<List<HealthRecordDto>> =
        safeApiCall(json) { healthRecordApi.list(patientId, limit = limit, fields = TREND_FIELDS) }

    private companion object {
        /**
         * The four metrics AN-05 charts. These are the backend's short allowlist names
         * (HealthRecordMapper.KNOWN_FIELDS), NOT the DTO's field names — passing "weightKg"
         * here instead of "weight" silently nulls out every metric and yields empty charts.
         */
        const val TREND_FIELDS = "weight,bodyFat,bmi,skeletalMuscleMass"
    }
}
