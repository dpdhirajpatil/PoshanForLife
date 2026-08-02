package com.poshanforlife.api.dto;

/**
 * Response to POST /auth/otp/request. Deliberately does not echo the code —
 * not even outside production, so a misconfigured environment can never turn
 * the endpoint itself into an OTP oracle. The local-profile bypass works by
 * accepting a fixed code at verify time instead (see PhoneOtpService).
 *
 * @param sent             always true; a failed send is an error response, not {@code false}
 * @param expiresInSeconds how long the code stays valid
 */
public record OtpRequestResponse(boolean sent, long expiresInSeconds) {
}
