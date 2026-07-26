package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

@Serializable
data class AppNotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val relatedEntityType: String? = null,
    val relatedEntityId: String? = null,
    val createdAt: String,
)

@Serializable
data class NotificationListResponseDto(
    val notifications: List<AppNotificationDto> = emptyList(),
    val unreadCount: Long = 0,
)
