package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.OrderDetailDto
import com.poshanforlife.android.core.network.OrderListItemDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateOrderRequest

/** Orders — server-scoped to the caller's own patients for a DOCTOR; ADMIN sees all. */
interface OrderRepository {
    suspend fun list(
        status: String? = null,
        paymentStatus: String? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        page: Int = 1,
        limit: Int = 20,
    ): Result<List<OrderListItemDto>>

    suspend fun getDetail(id: String): Result<OrderDetailDto>

    suspend fun update(id: String, request: UpdateOrderRequest): Result<OrderDetailDto>
}
