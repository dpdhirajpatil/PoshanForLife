package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.AppointmentStatus;

import java.time.Instant;

public record AppointmentDto(
        String id,
        UserRefDto patient,
        UserRefDto practitioner,
        Instant scheduledAt,
        int durationMinutes,
        AppointmentStatus status,
        String notes,
        boolean isVideo,
        String videoRoomId,
        UserRefDto createdBy,
        Instant createdAt) {
}
