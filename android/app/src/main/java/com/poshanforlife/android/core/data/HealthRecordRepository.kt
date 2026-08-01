package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpsertHealthRecordResponseDto

interface HealthRecordRepository {
    /** source must be "patient_manual" or "wearable_sync" — see backend HealthRecordService. */
    suspend fun upsert(source: String, weightKg: Double?): Result<UpsertHealthRecordResponseDto>

    /** Chronological ascending; `limit` caps to the most recent N. Backs AN-05's trend charts. */
    suspend fun trends(patientId: String, limit: Int): Result<List<HealthRecordDto>>
}
