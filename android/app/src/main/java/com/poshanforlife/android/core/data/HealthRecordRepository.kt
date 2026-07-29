package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpsertHealthRecordResponseDto

interface HealthRecordRepository {
    /** source must be "patient_manual" or "wearable_sync" — see backend HealthRecordService. */
    suspend fun upsert(source: String, weightKg: Double?): Result<UpsertHealthRecordResponseDto>
}
