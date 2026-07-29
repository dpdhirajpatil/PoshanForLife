package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.HealthRecordApi
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
}
