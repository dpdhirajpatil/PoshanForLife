package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ConvertLeadRequest
import com.poshanforlife.android.core.network.ConvertLeadResponseDto
import com.poshanforlife.android.core.network.LeadActivityDto
import com.poshanforlife.android.core.network.LeadDetailDto
import com.poshanforlife.android.core.network.LeadPipelineResponseDto
import com.poshanforlife.android.core.network.Result

/** Full CRM lead pipeline for practitioners (server-scoped to their own leads) and admins (all leads). */
interface LeadRepository {
    suspend fun listPipeline(
        stage: String? = null,
        search: String? = null,
        followupToday: Boolean = false,
        page: Int = 1,
        limit: Int = 20,
    ): Result<LeadPipelineResponseDto>

    suspend fun getLead(id: String): Result<LeadDetailDto>

    suspend fun updateStage(id: String, stage: String): Result<LeadDetailDto>

    suspend fun addActivity(id: String, activityType: String, description: String): Result<LeadActivityDto>

    suspend fun convert(id: String, request: ConvertLeadRequest): Result<ConvertLeadResponseDto>

    suspend fun scheduleFollowup(id: String, followupAt: String, message: String?): Result<LeadDetailDto>
}
