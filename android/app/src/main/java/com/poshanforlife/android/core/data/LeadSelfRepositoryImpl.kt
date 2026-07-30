package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.LeadSelfApi
import com.poshanforlife.android.core.network.RequestConsultationRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class LeadSelfRepositoryImpl @Inject constructor(
    private val leadSelfApi: LeadSelfApi,
    private val json: Json,
) : LeadSelfRepository {

    override suspend fun requestConsultation(preferredContactTime: String?, message: String?): Result<Unit> =
        when (
            val result = safeApiCall(json) {
                leadSelfApi.requestConsultation(RequestConsultationRequest(preferredContactTime, message))
            }
        ) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
}
