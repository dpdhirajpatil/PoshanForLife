package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/otp/verify.
 *
 * <p>{@code name} is required for SIGNUP only — a brand-new account needs a
 * display name, while LOGIN and ADD_PHONE act on a user that already has one.
 * That's enforced in PhoneOtpService rather than by an annotation here, since
 * it's conditional on purpose.
 */
public record OtpVerifyRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") String otp,
        @NotNull OtpPurpose purpose,
        @Size(min = 2, max = 255) String name) {
}
