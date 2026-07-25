package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/** Success envelope shape returned by the backend for every 2xx response. */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val meta: Meta? = null,
)

/** Pagination metadata, present on list endpoints only. */
@Serializable
data class Meta(
    val total: Long? = null,
    val page: Int? = null,
    val limit: Int? = null,
)

/** Error envelope shape returned by the backend for every non-2xx response. */
@Serializable
data class ApiError(
    val success: Boolean,
    val error: String,
    val code: String,
    val details: String? = null,
)
