package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.NotificationListResponseDto
import com.poshanforlife.android.core.network.Result

interface NotificationRepository {
    suspend fun list(limit: Int = 50, unreadOnly: Boolean = false): Result<NotificationListResponseDto>
    suspend fun markAllRead(): Result<Map<String, Boolean>>
}
