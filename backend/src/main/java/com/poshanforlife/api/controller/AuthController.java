package com.poshanforlife.api.controller;

import com.poshanforlife.api.config.RateLimit;
import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.AuthResponse;
import com.poshanforlife.api.dto.LoginRequest;
import com.poshanforlife.api.dto.RefreshTokenRequest;
import com.poshanforlife.api.dto.SignupRequest;
import com.poshanforlife.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
