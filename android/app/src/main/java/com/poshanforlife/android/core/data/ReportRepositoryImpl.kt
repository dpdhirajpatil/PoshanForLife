package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ReportApi
import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.ReportListItemDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val reportApi: ReportApi,
    private val json: Json,
) : ReportRepository {

    override suspend fun listInBodyReports(search: String?): Result<List<ReportListItemDto>> {
        val result = safeApiCall(json) { reportApi.list(search = search) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.reports)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getReport(id: String): Result<ReportDetailDto> =
        safeApiCall(json) { reportApi.get(id) }
}
