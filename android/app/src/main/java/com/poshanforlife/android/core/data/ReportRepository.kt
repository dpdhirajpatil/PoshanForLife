package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.ReportDetailDto
import com.poshanforlife.android.core.network.ReportListItemDto
import com.poshanforlife.android.core.network.Result

interface ReportRepository {
    suspend fun listInBodyReports(search: String? = null): Result<List<ReportListItemDto>>
    suspend fun getReport(id: String): Result<ReportDetailDto>
}
