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
        /**
         * Whether {@code phone} has been proven by OTP. Exposed so a client can
         * tell an unverified number typed into a profile apart from one that can
         * actually be used to sign in — the "link your phone" prompt keys off this,
         * not off phone being non-null.
         */
        boolean phoneVerified,
        String avatarUrl,
        LocalDate dateOfBirth,
        boolean isActive,
        NotificationPrefs notificationPrefs,
        Instant createdAt,
        Instant updatedAt) {
}
