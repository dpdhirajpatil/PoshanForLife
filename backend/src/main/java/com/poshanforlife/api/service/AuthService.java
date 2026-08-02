package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AuthResponse;
import com.poshanforlife.api.dto.LoginRequest;
import com.poshanforlife.api.dto.SignupRequest;
import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.RefreshToken;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.EmailConflictException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.mapper.UserMapper;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.RefreshTokenRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.JwtProperties;
import com.poshanforlife.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** One message for both unknown email and wrong password — never leak which. */
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LeadRepository leadRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Mobile self-signup, PUBLIC (no auth required). Transactionally: creates
     * a role=LEAD User, a linked Lead (source=mobile_app, stage=new — so the
     * CRM/Leads feature sees this person immediately), notifies every active
     * ADMIN, and issues the same token pair as login. The Lead's
     * convertedPatient link is set to this same User from the start (not just
     * on conversion) so LeadService.convert can promote it in place later
     * instead of creating a duplicate account.
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailConflictException(email);
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.LEAD);
        user.setPhone(request.phone());
        user = userRepository.save(user);

        createLeadFor(user, request.phone(), email, request.city(), request.healthGoal());
        return issueTokens(user);
    }

    /**
     * The phone-OTP counterpart to {@link #signup}: same role, same Lead
     * linkage, same notifications — the only differences are that identity is
     * proven by a verified phone instead of an email, and the account has no
     * password at all. A LEAD created this way is indistinguishable downstream,
     * so conversion to PATIENT needs no special casing.
     *
     * <p>Called only by PhoneOtpService, after the OTP has been verified.
     *
     * @param phone already normalised to E.164 and confirmed unclaimed
     */
    @Transactional
    public AuthResponse signupWithVerifiedPhone(String phone, String name) {
        User user = new User();
        user.setName(name.trim());
        user.setEmail(null);
        user.setPasswordHash(null);
        user.setRole(Role.LEAD);
        user.setPhone(phone);
        user.setPhoneVerified(true);
        user = userRepository.save(user);

        createLeadFor(user, phone, null, null, null);
        return issueTokens(user);
    }

    /** Token pair for an already-authenticated-by-other-means user (e.g. a verified OTP login). */
    @Transactional
    public AuthResponse issueTokensFor(User user) {
        return issueTokens(user);
    }

    /**
     * Creates the CRM Lead row that mirrors a self-signup account and tells
     * every active admin about it. convertedPatient points at the new user
     * from the start so LeadService.convert promotes in place rather than
     * creating a duplicate.
     */
    private void createLeadFor(User user, String phone, String email, String city, String healthGoal) {
        Lead lead = new Lead();
        lead.setName(user.getName());
        lead.setPhone(phone);
        lead.setEmail(email);
        lead.setCity(city);
        lead.setSource(LeadSource.MOBILE_APP);
        lead.setHealthGoal(healthGoal);
        lead.setStage(LeadStage.NEW);
        lead.setConvertedPatient(user);
        lead.setCreatedBy(user);
        lead = leadRepository.save(lead);

        for (User admin : userRepository.findByRoleAndIsActiveTrue(Role.ADMIN)) {
            notificationService.create(admin, Notification.TYPE_NEW_LEAD_SIGNUP,
                    "New sign-up", user.getName() + " signed up via the mobile app",
                    "lead", lead.getId());
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, BAD_CREDENTIALS));

        // A phone-signup account has no password at all. Reject before reaching
        // the encoder, which would otherwise log a warning on every attempt.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, BAD_CREDENTIALS);
        }
        // Deactivated (soft-deleted) accounts get the same opaque message
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, BAD_CREDENTIALS);
        }
        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a new access token, rotating the
     * refresh token: the presented one is revoked and a fresh one is returned.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .filter(RefreshToken::isUsable)
                .filter(token -> token.getUser().isActive())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "Invalid or expired refresh token"));

        stored.setRevoked(true);
        return issueTokens(stored.getUser());
    }

    /** Idempotent — revoking an unknown/already-revoked token is still a success. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(
                user.getId().toString(), user.getEmail(), user.getRole());

        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256(refreshToken));
        entity.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshExpiryMs()));
        refreshTokenRepository.save(entity);

        return new AuthResponse(accessToken, refreshToken, userMapper.toDto(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
