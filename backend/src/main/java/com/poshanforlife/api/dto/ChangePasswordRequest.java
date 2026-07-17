package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * currentPassword is required unless an ADMIN is changing someone else's
 * password (enforced in the service; nullable here).
 */
public record ChangePasswordRequest(
        String currentPassword,
        @NotBlank
        @Pattern(regexp = "^(?=.*\\d).{8,}$", message = "must be at least 8 characters and contain a digit")
        String newPassword,
        @NotBlank String confirmPassword) {
}
