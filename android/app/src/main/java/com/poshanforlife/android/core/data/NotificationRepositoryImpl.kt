package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.NotificationApi
import com.poshanforlife.android.core.network.NotificationListResponseDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi,
    private val json: Json,
) : NotificationRepository {

    override suspend fun list(limit: Int, unreadOnly: Boolean): Result<NotificationListResponseDto> =
        safeApiCall(json) { notificationApi.list(limit = limit, unread = unreadOnly) }

    override suspend fun markAllRead(): Result<Map<String, Boolean>> =
        safeApiCall(json) { notificationApi.markAllRead() }
}
