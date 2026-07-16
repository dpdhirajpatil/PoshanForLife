package com.poshanforlife.api.dto;

public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
}
