package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.TransactionDetailDto
import com.poshanforlife.android.core.network.TransactionListResponseDto
import com.poshanforlife.android.core.network.UserDetailDto

/** The financial ledger — server-scoped to the caller's own patients for a DOCTOR. */
interface TransactionRepository {
    suspend fun list(
        search: String? = null,
        userId: String? = null,
        catalogue: String? = null,
        paymentType: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        page: Int = 1,
        limit: Int = 20,
    ): Result<TransactionListResponseDto>

    suspend fun getDetail(id: String): Result<TransactionDetailDto>

    /** ADMIN-only server-side — feeds the "practitioner" filter picker. */
    suspend fun listPractitioners(search: String? = null): Result<List<UserDetailDto>>
}
