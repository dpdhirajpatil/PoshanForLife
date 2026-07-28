package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.OrderApi
import com.poshanforlife.android.core.network.OrderDetailDto
import com.poshanforlife.android.core.network.OrderListItemDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateOrderRequest
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class OrderRepositoryImpl @Inject constructor(
    private val orderApi: OrderApi,
    private val json: Json,
) : OrderRepository {

    override suspend fun list(
        status: String?,
        paymentStatus: String?,
        search: String?,
        dateFrom: String?,
        dateTo: String?,
        page: Int,
        limit: Int,
    ): Result<List<OrderListItemDto>> =
        safeApiCall(json) { orderApi.list(status, paymentStatus, search, dateFrom, dateTo, page, limit) }

    override suspend fun getDetail(id: String): Result<OrderDetailDto> =
        safeApiCall(json) { orderApi.get(id) }

    override suspend fun update(id: String, request: UpdateOrderRequest): Result<OrderDetailDto> =
        safeApiCall(json) { orderApi.update(id, request) }
}
