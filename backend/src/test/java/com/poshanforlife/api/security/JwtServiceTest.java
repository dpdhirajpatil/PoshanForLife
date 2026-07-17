package com.poshanforlife.api.security;

import com.poshanforlife.api.entity.Role;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("01234567890123456789012345678901", 60_000L, 120_000L));

    @Test
    void generatesAndParsesAValidToken() {
        String token = jwtService.generateToken("user-123", "doctor@example.com", Role.DOCTOR);

        Optional<AuthenticatedUser> result = jwtService.parseToken(token);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo("user-123");
        assertThat(result.orElseThrow().email()).isEqualTo("doctor@example.com");
        assertThat(result.orElseThrow().role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void returnsEmptyWhenTokenIsInvalid() {
        assertThat(jwtService.parseToken("not-a-valid-token")).isEmpty();
    }
}
