package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Role;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PATCH body — all fields optional; absent (null) fields are left unchanged.
 * name/phone: self or admin. role/isActive/dateOfBirth: admin only — a
 * non-admin sending them gets 422 VALIDATION_ERROR (rejected, not ignored).
 */
public record UpdateUserRequest(
        @Size(min = 2, max = 255) String name,
        @Size(max = 32) String phone,
        Role role,
        Boolean isActive,
        LocalDate dateOfBirth) {
}
