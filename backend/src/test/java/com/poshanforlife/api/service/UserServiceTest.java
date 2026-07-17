package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AssignPatientsRequest;
import com.poshanforlife.api.dto.ChangePasswordRequest;
import com.poshanforlife.api.dto.CreateUserRequest;
import com.poshanforlife.api.dto.NotificationPrefsRequest;
import com.poshanforlife.api.dto.UpdateUserRequest;
import com.poshanforlife.api.entity.DoctorPatient;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.EmailConflictException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.mapper.UserMapperImpl;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.RefreshTokenRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    private User doctor;
    private AuthenticatedUser adminCaller;
    private AuthenticatedUser doctorCaller;

    @BeforeEach
    void setUp() throws Exception {
        userService = new UserService(userRepository, doctorPatientRepository,
                refreshTokenRepository, passwordEncoder, new UserMapperImpl());

        doctor = newUser("Dr. Jones", "dr@poshanforlife.com", Role.DOCTOR, "Doc@1234");
        adminCaller = new AuthenticatedUser(UUID.randomUUID().toString(), "admin@x.com", Role.ADMIN);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), doctor.getEmail(), Role.DOCTOR);
    }

    @Test
    void createRejectsDuplicateEmailWith409() {
        when(userRepository.existsByEmail("dr@poshanforlife.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("Dr. Jones", "DR@PoshanForLife.com", "Passw0rd!", Role.DOCTOR, null)))
                .isInstanceOfSatisfying(EmailConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_CONFLICT));
    }

    @Test
    void createHashesPasswordAndNeverExposesIt() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = userService.create(
                new CreateUserRequest("New Doc", "new@poshanforlife.com", "Passw0rd!", Role.DOCTOR, "999"));

        verify(userRepository).save(any(User.class));
        assertThat(dto.toString()).doesNotContain("Passw0rd!");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.notificationPrefs().inbodyReport()).isTrue();
    }

    @Test
    void nonAdminSelfUpdateCanChangeNameAndPhone() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        var dto = userService.update(doctor.getId(),
                new UpdateUserRequest("New Name", "12345", null, null, null), doctorCaller);

        assertThat(dto.name()).isEqualTo("New Name");
        assertThat(dto.phone()).isEqualTo("12345");
    }

    @Test
    void nonAdminSelfUpdateSendingAdminFieldsIsRejected() {
        assertThatThrownBy(() -> userService.update(doctor.getId(),
                new UpdateUserRequest(null, null, Role.ADMIN, null, null), doctorCaller))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void nonAdminCannotTouchAnotherUser() {
        assertThatThrownBy(() -> userService.get(UUID.randomUUID(), doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void softDeleteDeactivatesAndRevokesTokens() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        userService.softDelete(doctor.getId());

        assertThat(doctor.isActive()).isFalse();
        verify(refreshTokenRepository).revokeAllForUser(doctor.getId());
    }

    @Test
    void selfPasswordChangeRequiresCorrectCurrentPassword() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> userService.changePassword(doctor.getId(),
                new ChangePasswordRequest("wrong-current", "NewPass1", "NewPass1"), doctorCaller))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void adminCanChangeAnotherUsersPasswordWithoutCurrentPassword() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        userService.changePassword(doctor.getId(),
                new ChangePasswordRequest(null, "NewPass1", "NewPass1"), adminCaller);

        assertThat(passwordEncoder.matches("NewPass1", doctor.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(doctor.getId());
    }

    @Test
    void mismatchedConfirmPasswordIsRejected() {
        assertThatThrownBy(() -> userService.changePassword(doctor.getId(),
                new ChangePasswordRequest(null, "NewPass1", "Different1"), adminCaller))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void assignPatientsReplacesExistingAssignmentsInOneTransaction() throws Exception {
        User patient1 = newUser("P1", "p1@x.com", Role.PATIENT, "Pass1234");
        User patient2 = newUser("P2", "p2@x.com", Role.PATIENT, "Pass1234");
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(userRepository.findByIdInAndRole(anyList(), any()))
                .thenReturn(List.of(patient1, patient2));

        var result = userService.assignPatients(doctor.getId(),
                new AssignPatientsRequest(List.of(patient1.getId(), patient2.getId())));

        verify(doctorPatientRepository).deleteByDoctorId(doctor.getId());
        verify(doctorPatientRepository, times(2)).save(any(DoctorPatient.class));
        assertThat(result).hasSize(2);
    }

    @Test
    void assignPatientsRejectsNonDoctorTargetsAndUnknownPatients() {
        User patientTarget = newUserQuiet("P", "p@x.com", Role.PATIENT);
        when(userRepository.findById(patientTarget.getId())).thenReturn(Optional.of(patientTarget));
        assertThatThrownBy(() -> userService.assignPatients(patientTarget.getId(),
                new AssignPatientsRequest(List.of())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(userRepository.findByIdInAndRole(anyList(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> userService.assignPatients(doctor.getId(),
                new AssignPatientsRequest(List.of(UUID.randomUUID()))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(doctorPatientRepository, never()).deleteByDoctorId(any());
    }

    @Test
    void notificationPrefsMergeKeepsUnsentFields() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        var dto = userService.updateNotificationPrefs(doctor.getId(),
                new NotificationPrefsRequest(false, null, null, null), doctorCaller);

        assertThat(dto.notificationPrefs().inbodyReport()).isFalse();
        assertThat(dto.notificationPrefs().patientAssigned()).isTrue();
        assertThat(dto.notificationPrefs().systemAnnouncements()).isTrue();
    }

    private User newUser(String name, String email, Role role, String rawPassword) throws Exception {
        User user = newUserQuiet(name, email, role);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    private User newUserQuiet(String name, String email, Role role) {
        User user = new User();
        try {
            Field idField = User.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash("unset");
        return user;
    }
}
