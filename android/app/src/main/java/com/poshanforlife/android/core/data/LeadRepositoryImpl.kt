package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ConvertLeadRequest
import com.poshanforlife.android.core.network.ConvertLeadResponseDto
import com.poshanforlife.android.core.network.CreateLeadActivityRequest
import com.poshanforlife.android.core.network.LeadActivityDto
import com.poshanforlife.android.core.network.LeadApi
import com.poshanforlife.android.core.network.LeadDetailDto
import com.poshanforlife.android.core.network.LeadPipelineResponseDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.ScheduleFollowupRequest
import com.poshanforlife.android.core.network.UpdateLeadRequest
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class LeadRepositoryImpl @Inject constructor(
    private val leadApi: LeadApi,
    private val json: Json,
) : LeadRepository {

    override suspend fun listPipeline(
        stage: String?,
        search: String?,
        followupToday: Boolean,
        page: Int,
        limit: Int,
    ): Result<LeadPipelineResponseDto> =
        safeApiCall(json) { leadApi.listPipeline(stage, search, followupToday, page, limit) }

    override suspend fun getLead(id: String): Result<LeadDetailDto> =
        safeApiCall(json) { leadApi.get(id) }

    override suspend fun updateStage(id: String, stage: String): Result<LeadDetailDto> =
        safeApiCall(json) { leadApi.update(id, UpdateLeadRequest(stage)) }

    override suspend fun addActivity(id: String, activityType: String, description: String): Result<LeadActivityDto> =
        safeApiCall(json) { leadApi.addActivity(id, CreateLeadActivityRequest(activityType, description)) }

    override suspend fun convert(id: String, request: ConvertLeadRequest): Result<ConvertLeadResponseDto> =
        safeApiCall(json) { leadApi.convert(id, request) }

    override suspend fun scheduleFollowup(id: String, followupAt: String, message: String?): Result<LeadDetailDto> =
        safeApiCall(json) { leadApi.scheduleFollowup(id, ScheduleFollowupRequest(followupAt, message)) }
}
