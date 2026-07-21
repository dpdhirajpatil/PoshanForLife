package com.poshanforlife.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        String id,
        String type,
        String title,
        String message,
        boolean read,
        String relatedEntityType,
        UUID relatedEntityId,
        Instant createdAt) {
}
