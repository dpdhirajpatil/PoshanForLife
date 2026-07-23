package com.poshanforlife.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mobile self-signup (POST /auth/signup, public). Creates a role=LEAD User
 * plus a linked Lead record (source=mobile_app) in one transaction — see
 * AuthService.signup. city/healthGoal are free text, stored on the Lead
 * rather than the User (matching how staff-entered leads capture them).
 */
public record SignupRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*\\d).{8,}$", message = "must be at least 8 characters and contain a digit")
        String password,
        @Size(max = 32) String phone,
        @Size(max = 100) String city,
        @Size(max = 2000) String healthGoal) {
}
