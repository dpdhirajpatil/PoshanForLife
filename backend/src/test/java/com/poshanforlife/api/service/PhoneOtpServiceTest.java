package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AuthResponse;
import com.poshanforlife.api.dto.OtpRequestRequest;
import com.poshanforlife.api.dto.OtpVerifyRequest;
import com.poshanforlife.api.entity.OtpPurpose;
import com.poshanforlife.api.entity.PhoneOtp;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.mapper.UserMapper;
import com.poshanforlife.api.repository.PhoneOtpRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhoneOtpServiceTest {

    private static final String RAW_PHONE = "98765 43210";
    private static final String E164 = "+919876543210";

    @Mock
    private PhoneOtpRepository phoneOtpRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OtpSmsClient otpSmsClient;
    @Mock
    private OtpAttemptRecorder otpAttemptRecorder;
    @Mock
    private AuthService authService;
    @Mock
    private UserMapper userMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PhoneOtpService service;

    /** Profile-free environment: the dev bypass is OFF unless a test turns it on. */
    private PhoneOtpService serviceWith(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new PhoneOtpService(phoneOtpRepository, userRepository, otpSmsClient,
                otpAttemptRecorder, authService, passwordEncoder, userMapper, env);
    }

    @BeforeEach
    void setUp() {
        service = serviceWith();
        when(phoneOtpRepository.save(any(PhoneOtp.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- request ----------

    @Test
    void requestSignupNormalisesPhoneAndSendsSms() {
        when(userRepository.existsByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(false);
        when(phoneOtpRepository.countByPhoneAndCreatedAtAfter(eq(E164), any())).thenReturn(0L);

        var response = service.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.SIGNUP), null);

        assertThat(response.sent()).isTrue();
        assertThat(response.expiresInSeconds()).isEqualTo(600);

        ArgumentCaptor<PhoneOtp> saved = ArgumentCaptor.forClass(PhoneOtp.class);
        verify(phoneOtpRepository).save(saved.capture());
        // Stored in canonical form, and never as the raw code.
        assertThat(saved.getValue().getPhone()).isEqualTo(E164);
        assertThat(saved.getValue().getOtpHash()).startsWith("$2");
        verify(otpSmsClient).sendOtp(eq(E164), anyString());
    }

    @Test
    void requestSignupRejectsAlreadyRegisteredPhone() {
        when(userRepository.existsByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(true);

        assertThatThrownBy(() -> service.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.SIGNUP), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.PHONE_CONFLICT));
        verify(otpSmsClient, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void requestLoginRejectsUnknownPhone() {
        when(userRepository.findByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.LOGIN), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    @Test
    void requestAddPhoneRequiresAuthentication() {
        assertThatThrownBy(() -> service.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.ADD_PHONE), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    @Test
    void requestAddPhoneRejectsNumberOwnedByAnotherUser() {
        User caller = user(UUID.randomUUID(), Role.PATIENT);
        User otherOwner = user(UUID.randomUUID(), Role.PATIENT);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(Optional.of(otherOwner));

        assertThatThrownBy(() -> service.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.ADD_PHONE),
                principal(caller)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.PHONE_CONFLICT));
    }

    @Test
    void requestEnforcesPerPhoneSendLimit() {
        when(userRepository.existsByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(false);
        when(phoneOtpRepository.countByPhoneAndCreatedAtAfter(eq(E164), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.SIGNUP), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
        verify(otpSmsClient, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void localProfileSkipsSmsSend() {
        PhoneOtpService local = serviceWith("local");
        when(userRepository.existsByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(false);
        when(phoneOtpRepository.countByPhoneAndCreatedAtAfter(eq(E164), any())).thenReturn(0L);

        local.request(new OtpRequestRequest(RAW_PHONE, OtpPurpose.SIGNUP), null);

        verify(otpSmsClient, never()).sendOtp(anyString(), anyString());
    }

    // ---------- verify ----------

    @Test
    void verifySignupCreatesLeadAccount() {
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        when(userRepository.existsByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(false);
        AuthResponse expected = new AuthResponse("access", "refresh", null);
        when(authService.signupWithVerifiedPhone(E164, "Asha")).thenReturn(expected);

        AuthResponse actual = service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.SIGNUP, "Asha"), null);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void verifySignupRequiresName() {
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().plus(5, ChronoUnit.MINUTES), 0);

        assertThatThrownBy(() -> service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.SIGNUP, null), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("name is required");
        verify(authService, never()).signupWithVerifiedPhone(anyString(), anyString());
    }

    @Test
    void verifyWrongCodeRecordsAttemptOutsideTheRolledBackTransaction() {
        PhoneOtp pending = stubPendingOtp("123456", OtpPurpose.SIGNUP,
                Instant.now().plus(5, ChronoUnit.MINUTES), 2);

        assertThatThrownBy(() -> service.verify(
                new OtpVerifyRequest(RAW_PHONE, "999999", OtpPurpose.SIGNUP, "Asha"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("isn't correct");

        // Must go through the REQUIRES_NEW recorder, not a plain save: throwing
        // rolls this transaction back, so an inline increment would be lost and
        // the attempt limit would never bite. (Mocks can't see transaction
        // boundaries — the live-DB check is what originally caught this.)
        verify(otpAttemptRecorder).recordFailedAttempt(pending.getId());
        verify(phoneOtpRepository, never()).save(pending);
    }

    @Test
    void verifyRejectsAfterMaxAttempts() {
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().plus(5, ChronoUnit.MINUTES), 5);

        assertThatThrownBy(() -> service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.SIGNUP, "Asha"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many incorrect attempts");
        // Even the correct code is refused once the row is spent.
        verify(authService, never()).signupWithVerifiedPhone(anyString(), anyString());
    }

    @Test
    void verifyRejectsExpiredCode() {
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().minus(1, ChronoUnit.MINUTES), 0);

        assertThatThrownBy(() -> service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.SIGNUP, "Asha"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyLoginIssuesTokensForExistingUser() {
        stubPendingOtp("123456", OtpPurpose.LOGIN, Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        User existing = user(UUID.randomUUID(), Role.PATIENT);
        when(userRepository.findByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(Optional.of(existing));
        AuthResponse expected = new AuthResponse("access", "refresh", null);
        when(authService.issueTokensFor(existing)).thenReturn(expected);

        AuthResponse actual = service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.LOGIN, null), null);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void verifyLoginRejectsDeactivatedAccount() {
        stubPendingOtp("123456", OtpPurpose.LOGIN, Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        User existing = user(UUID.randomUUID(), Role.PATIENT);
        existing.setActive(false);
        when(userRepository.findByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.LOGIN, null), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    @Test
    void verifyAddPhoneAttachesNumberAndReturnsNoTokens() {
        stubPendingOtp("123456", OtpPurpose.ADD_PHONE, Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        User caller = user(UUID.randomUUID(), Role.PATIENT);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(Optional.empty());

        AuthResponse response = service.verify(
                new OtpVerifyRequest(RAW_PHONE, "123456", OtpPurpose.ADD_PHONE, null), principal(caller));

        assertThat(caller.getPhone()).isEqualTo(E164);
        assertThat(caller.isPhoneVerified()).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    void devFixedCodeIsRejectedWithoutADevProfile() {
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().plus(5, ChronoUnit.MINUTES), 0);

        assertThatThrownBy(() -> service.verify(
                new OtpVerifyRequest(RAW_PHONE, "000000", OtpPurpose.SIGNUP, "Asha"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("isn't correct");
    }

    @Test
    void devFixedCodeWorksUnderLocalProfile() {
        PhoneOtpService local = serviceWith("local");
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        when(userRepository.existsByPhoneAndPhoneVerifiedTrue(E164)).thenReturn(false);
        when(authService.signupWithVerifiedPhone(E164, "Asha"))
                .thenReturn(new AuthResponse("a", "r", null));

        AuthResponse response = local.verify(
                new OtpVerifyRequest(RAW_PHONE, "000000", OtpPurpose.SIGNUP, "Asha"), null);

        assertThat(response.accessToken()).isEqualTo("a");
    }

    /** prod wins even if someone also lists local — the bypass must never be reachable there. */
    @Test
    void devFixedCodeIsRejectedWhenProdIsAlsoActive() {
        PhoneOtpService prod = serviceWith("local", "prod");
        stubPendingOtp("123456", OtpPurpose.SIGNUP, Instant.now().plus(5, ChronoUnit.MINUTES), 0);

        assertThatThrownBy(() -> prod.verify(
                new OtpVerifyRequest(RAW_PHONE, "000000", OtpPurpose.SIGNUP, "Asha"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("isn't correct");
    }

    // ---------- helpers ----------

    private PhoneOtp stubPendingOtp(String code, OtpPurpose purpose, Instant expiresAt, int attempts) {
        PhoneOtp otp = new PhoneOtp();
        otp.setPhone(E164);
        otp.setPurpose(purpose);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(expiresAt);
        otp.setAttempts(attempts);
        when(phoneOtpRepository.findFirstByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(E164, purpose))
                .thenReturn(Optional.of(otp));
        return otp;
    }

    private static User user(UUID id, Role role) {
        User user = new User();
        user.setName("Test");
        user.setRole(role);
        setId(user, id);
        return user;
    }

    private static AuthenticatedUser principal(User user) {
        return new AuthenticatedUser(user.getId().toString(), user.getEmail(), user.getRole());
    }

    private static void setId(User user, UUID id) {
        try {
            Field f = User.class.getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
