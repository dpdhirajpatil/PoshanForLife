package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.NotificationPrefs;
import com.poshanforlife.api.entity.Role;

import java.time.Instant;
import java.time.LocalDate;

/** Full user representation for the users feature. Never carries the password hash. */
public record UserDetailDto(
        String id,
        String name,
        String email,
        Role role,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth,
        boolean isActive,
        NotificationPrefs notificationPrefs,
        Instant createdAt,
        Instant updatedAt) {
}
