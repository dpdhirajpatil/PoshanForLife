package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AuthResponse;
import com.poshanforlife.api.dto.OtpRequestRequest;
import com.poshanforlife.api.dto.OtpRequestResponse;
import com.poshanforlife.api.dto.OtpVerifyRequest;
import com.poshanforlife.api.entity.OtpPurpose;
import com.poshanforlife.api.entity.PhoneOtp;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.mapper.UserMapper;
import com.poshanforlife.api.repository.PhoneOtpRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Phone-number login and signup by one-time password, for self-service
 * (PATIENT/LEAD) accounts only. Staff accounts are created by an admin and
 * keep email+password — there is deliberately no phone signup path to an
 * ADMIN or DOCTOR role here.
 *
 * <p>Three defences against abuse, each covering a different attack:
 * <ul>
 *   <li>a per-phone send limit, so one number can't be used to bill out SMS;
 *   <li>a per-code attempt limit, so a 6-digit code can't be brute-forced;
 *   <li>a short expiry, so an intercepted code has a small window.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneOtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration SEND_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Accepted for any number when the local/dev bypass is active, so local
     * work doesn't burn real SMS. Hard-disabled under the prod profile.
     */
    private static final String DEV_FIXED_OTP = "000000";

    private final PhoneOtpRepository phoneOtpRepository;
    private final UserRepository userRepository;
    private final OtpSmsClient otpSmsClient;
    private final OtpAttemptRecorder otpAttemptRecorder;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final Environment environment;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Issues and sends a code. Validation differs by purpose so a caller can't
     * probe for whether a number is registered using the wrong flow.
     *
     * @param caller the authenticated user for ADD_PHONE; null otherwise
     */
    @Transactional
    public OtpRequestResponse request(OtpRequestRequest request, AuthenticatedUser caller) {
        String phone = PhoneNumbers.normalize(request.phone());
        OtpPurpose purpose = request.purpose();

        User linkedUser = null;
        switch (purpose) {
            case SIGNUP -> {
                if (userRepository.existsByPhoneAndPhoneVerifiedTrue(phone)) {
                    throw new ApiException(ErrorCode.PHONE_CONFLICT,
                            "An account already exists for this phone number");
                }
            }
            case LOGIN -> {
                // Same opaque wording as password login: don't confirm which
                // numbers are registered to an unauthenticated caller.
                userRepository.findByPhoneAndPhoneVerifiedTrue(phone)
                        .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED,
                                "No account found for this phone number"));
            }
            case ADD_PHONE -> {
                linkedUser = requireCaller(caller);
                // Their own number re-confirmed is fine; someone else's is not.
                User owner = userRepository.findByPhoneAndPhoneVerifiedTrue(phone).orElse(null);
                if (owner != null && !owner.getId().equals(linkedUser.getId())) {
                    throw new ApiException(ErrorCode.PHONE_CONFLICT,
                            "That phone number is already in use");
                }
            }
        }

        enforceSendLimit(phone);

        String otp = generateOtp();
        PhoneOtp record = new PhoneOtp();
        record.setPhone(phone);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setPurpose(purpose);
        record.setExpiresAt(Instant.now().plus(OTP_TTL));
        record.setUser(linkedUser);
        phoneOtpRepository.save(record);

        if (devBypassEnabled()) {
            // Server-side only, and only off-prod: the response never carries it.
            log.warn("DEV OTP for {} ({}): {} — fixed code {} also accepted",
                    phone, purpose, otp, DEV_FIXED_OTP);
        } else {
            otpSmsClient.sendOtp(phone, otp);
        }

        return new OtpRequestResponse(true, OTP_TTL.toSeconds());
    }

    /**
     * Checks a code and performs the action it was issued for.
     *
     * @return tokens plus profile for SIGNUP/LOGIN; for ADD_PHONE the profile
     *         only, with null tokens — the caller is already authenticated and
     *         their existing token stays valid.
     */
    @Transactional
    public AuthResponse verify(OtpVerifyRequest request, AuthenticatedUser caller) {
        String phone = PhoneNumbers.normalize(request.phone());
        OtpPurpose purpose = request.purpose();

        PhoneOtp record = phoneOtpRepository
                .findFirstByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(phone, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Request a verification code first"));

        if (Instant.now().isAfter(record.getExpiresAt())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "That code has expired. Request a new one.");
        }
        if (record.getAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Too many incorrect attempts. Request a new code.");
        }

        boolean matches = passwordEncoder.matches(request.otp(), record.getOtpHash())
                || (devBypassEnabled() && DEV_FIXED_OTP.equals(request.otp()));
        if (!matches) {
            // Recorded in its own transaction: throwing below rolls this one
            // back, which would otherwise undo the increment and leave the
            // attempt limit permanently at zero. See OtpAttemptRecorder.
            otpAttemptRecorder.recordFailedAttempt(record.getId());
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "That code isn't correct");
        }

        // Also committed separately, so a code is spent the moment it's used
        // and can't be replayed if the action below fails and rolls back.
        otpAttemptRecorder.markVerified(record.getId());

        return switch (purpose) {
            case SIGNUP -> completeSignup(phone, request.name());
            case LOGIN -> completeLogin(phone);
            case ADD_PHONE -> completeAddPhone(phone, requireCaller(caller));
        };
    }

    private AuthResponse completeSignup(String phone, String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Your name is required to create an account");
        }
        // Re-checked after verification, not just at request time: someone else
        // could have completed a signup for this number in between.
        if (userRepository.existsByPhoneAndPhoneVerifiedTrue(phone)) {
            throw new ApiException(ErrorCode.PHONE_CONFLICT,
                    "An account already exists for this phone number");
        }
        return authService.signupWithVerifiedPhone(phone, name);
    }

    private AuthResponse completeLogin(String phone) {
        User user = userRepository.findByPhoneAndPhoneVerifiedTrue(phone)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED,
                        "No account found for this phone number"));
        return authService.issueTokensFor(user);
    }

    private AuthResponse completeAddPhone(String phone, User caller) {
        User owner = userRepository.findByPhoneAndPhoneVerifiedTrue(phone).orElse(null);
        if (owner != null && !owner.getId().equals(caller.getId())) {
            throw new ApiException(ErrorCode.PHONE_CONFLICT, "That phone number is already in use");
        }
        caller.setPhone(phone);
        caller.setPhoneVerified(true);
        userRepository.save(caller);
        // No tokens: the caller already holds a valid one, and their role and
        // id are unchanged, so nothing in the existing JWT went stale.
        return new AuthResponse(null, null, userMapper.toDto(caller));
    }

    /**
     * Counted in the database rather than the IP-keyed {@code @RateLimit}
     * interceptor, which can't see the phone number and resets on restart.
     * The interceptor still guards the endpoint against a single-IP flood;
     * this is the per-number limit on top.
     */
    private void enforceSendLimit(String phone) {
        long recent = phoneOtpRepository.countByPhoneAndCreatedAtAfter(
                phone, Instant.now().minus(SEND_WINDOW));
        if (recent >= MAX_SENDS_PER_WINDOW) {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many codes requested. Please wait a few minutes and try again.");
        }
    }

    private User requireCaller(AuthenticatedUser caller) {
        if (caller == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "Sign in to add a phone number");
        }
        return userRepository.findById(UUID.fromString(caller.id()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "Sign in to add a phone number"));
    }

    /** Six digits, zero-padded, from a cryptographic source. */
    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    /**
     * True only under the local/dev profiles. The explicit prod check comes
     * first so that even a misconfigured deployment listing both profiles can
     * never accept the fixed code.
     */
    private boolean devBypassEnabled() {
        String[] active = environment.getActiveProfiles();
        if (Arrays.stream(active).anyMatch(p -> p.equalsIgnoreCase("prod"))) {
            return false;
        }
        return Arrays.stream(active)
                .anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
    }
}
