package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AuthResponse;
import com.poshanforlife.api.dto.LoginRequest;
import com.poshanforlife.api.entity.RefreshToken;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.mapper.UserMapperImpl;
import com.poshanforlife.api.repository.RefreshTokenRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.JwtProperties;
import com.poshanforlife.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final JwtProperties JWT_PROPS = new JwtProperties(
            "test-secret-key-that-is-at-least-32-bytes-long!", 900_000, 1_209_600_000);

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(JWT_PROPS);

    private AuthService authService;
    private User admin;

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthService(userRepository, refreshTokenRepository,
                passwordEncoder, jwtService, JWT_PROPS, new UserMapperImpl());

        admin = new User();
        setId(admin, UUID.randomUUID());
        admin.setName("Admin");
        admin.setEmail("admin@poshanforlife.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
        admin.setRole(Role.ADMIN);
    }

    @Test
    void loginWithValidCredentialsIssuesBothTokensAndUser() {
        when(userRepository.findByEmail("admin@poshanforlife.com")).thenReturn(Optional.of(admin));

        AuthResponse response = authService.login(new LoginRequest("Admin@PoshanForLife.com ", "Admin@123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("admin@poshanforlife.com");
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        // access token round-trips through JwtService with the right claims
        assertThat(jwtService.parseToken(response.accessToken()))
                .hasValueSatisfying(principal -> assertThat(principal.role()).isEqualTo(Role.ADMIN));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithWrongPasswordFailsWithAuthRequired() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(admin));
        assertBadCredentials("admin@poshanforlife.com", "wrong");
    }

    @Test
    void loginWithUnknownEmailFailsWithSameMessageAsWrongPassword() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertBadCredentials("nobody@poshanforlife.com", "Admin@123");
    }

    @Test
    void refreshRotatesTheRefreshToken() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(admin));
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);

        AuthResponse first = authService.login(new LoginRequest("admin@poshanforlife.com", "Admin@123"));
        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken storedToken = saved.getValue();

        when(refreshTokenRepository.findByTokenHash(storedToken.getTokenHash()))
                .thenReturn(Optional.of(storedToken));

        AuthResponse second = authService.refresh(first.refreshToken());

        assertThat(storedToken.isRevoked()).as("presented token is revoked on rotation").isTrue();
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    }

    @Test
    void refreshWithRevokedOrExpiredTokenFails() {
        RefreshToken revoked = new RefreshToken();
        revoked.setUser(admin);
        revoked.setExpiresAt(Instant.now().plusSeconds(3600));
        revoked.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("some-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    @Test
    void logoutRevokesTheTokenAndIsIdempotent() {
        RefreshToken active = new RefreshToken();
        active.setUser(admin);
        active.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.empty());

        authService.logout("token");
        assertThat(active.isRevoked()).isTrue();
        authService.logout("token"); // unknown token — still no exception
    }

    private void assertBadCredentials(String email, String password) {
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, password)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.AUTH_REQUIRED);
                    assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
                });
    }

    private static void setId(User user, UUID id) throws Exception {
        Field idField = user.getClass().getSuperclass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
