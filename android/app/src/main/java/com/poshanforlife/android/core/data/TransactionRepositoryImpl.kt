package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.TransactionApi
import com.poshanforlife.android.core.network.TransactionDetailDto
import com.poshanforlife.android.core.network.TransactionListResponseDto
import com.poshanforlife.android.core.network.UserApi
import com.poshanforlife.android.core.network.UserDetailDto
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionApi: TransactionApi,
    private val userApi: UserApi,
    private val json: Json,
) : TransactionRepository {

    override suspend fun list(
        search: String?,
        userId: String?,
        catalogue: String?,
        paymentType: String?,
        dateFrom: String?,
        dateTo: String?,
        page: Int,
        limit: Int,
    ): Result<TransactionListResponseDto> =
        safeApiCall(json) { transactionApi.list(search, userId, catalogue, paymentType, dateFrom, dateTo, page, limit) }

    override suspend fun getDetail(id: String): Result<TransactionDetailDto> =
        safeApiCall(json) { transactionApi.get(id) }

    override suspend fun listPractitioners(search: String?): Result<List<UserDetailDto>> =
        safeApiCall(json) { userApi.list(role = "DOCTOR", search = search) }
}
