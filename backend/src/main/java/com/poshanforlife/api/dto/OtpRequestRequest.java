package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/otp/request. The phone is normalised to E.164 server-side, so
 * clients may send it in any reasonable local format.
 */
public record OtpRequestRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotNull OtpPurpose purpose) {
}
