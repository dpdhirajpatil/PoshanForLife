package com.poshanforlife.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessExpiryMs, long refreshExpiryMs) {
}
