package com.poshanforlife.api.dto;

import java.util.List;

public record NotificationListResponseDto(List<NotificationDto> notifications, long unreadCount) {
}
