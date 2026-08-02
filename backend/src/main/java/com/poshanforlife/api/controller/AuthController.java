package com.poshanforlife.api.controller;

import com.poshanforlife.api.config.RateLimit;
import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.AuthResponse;
import com.poshanforlife.api.dto.LoginRequest;
import com.poshanforlife.api.dto.OtpRequestRequest;
import com.poshanforlife.api.dto.OtpRequestResponse;
import com.poshanforlife.api.dto.OtpVerifyRequest;
import com.poshanforlife.api.dto.RefreshTokenRequest;
import com.poshanforlife.api.dto.SignupRequest;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.AuthService;
import com.poshanforlife.api.service.PhoneOtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PhoneOtpService phoneOtpService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(requests = 10, windowSeconds = 60)
    public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    @RateLimit(requests = 10, windowSeconds = 60)
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @RateLimit(requests = 30, windowSeconds = 60)
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(Map.of("loggedOut", true));
    }

    /**
     * Sends a one-time code. Public, but note the per-phone send limit inside
     * PhoneOtpService: this IP-keyed {@code @RateLimit} only stops a single
     * host flooding the endpoint, not one number being targeted from many.
     *
     * <p>For purpose=ADD_PHONE the caller must present a bearer token. The
     * path is under the permitAll {@code /auth/**} prefix, so that's enforced
     * in the service rather than by Spring Security.
     */
    @PostMapping("/otp/request")
    @RateLimit(requests = 20, windowSeconds = 60)
    public ApiResponse<OtpRequestResponse> requestOtp(
            @Valid @RequestBody OtpRequestRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(phoneOtpService.request(request, caller));
    }

    /**
     * Verifies a code and completes its purpose: SIGNUP and LOGIN return a
     * token pair, ADD_PHONE returns the updated profile with null tokens
     * (the caller's existing token is still valid — see PhoneOtpService).
     */
    @PostMapping("/otp/verify")
    @RateLimit(requests = 20, windowSeconds = 60)
    public ApiResponse<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(phoneOtpService.verify(request, caller));
    }
}
