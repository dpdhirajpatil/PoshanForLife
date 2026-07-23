package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreatePatientRequest;
import com.poshanforlife.api.dto.PatientStatsDto;
import com.poshanforlife.api.dto.UpdatePatientRequest;
import com.poshanforlife.api.entity.DoctorPatient;
import com.poshanforlife.api.entity.Gender;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.RefreshTokenRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.Page.empty;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private HealthRecordRepository healthRecordRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PatientService patientService;

    private AuthenticatedUser admin;
    private AuthenticatedUser doctor;
    private UUID doctorId;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(userRepository, patientProfileRepository,
                doctorPatientRepository, healthRecordRepository, refreshTokenRepository, passwordEncoder);
        doctorId = UUID.randomUUID();
        admin = new AuthenticatedUser(UUID.randomUUID().toString(), "admin@x.com", Role.ADMIN);
        doctor = new AuthenticatedUser(doctorId.toString(), "doc@x.com", Role.DOCTOR);
    }

    @Test
    void doctorListIsAlwaysScopedToOwnPatientsEvenWithDoctorIdParam() {
        when(userRepository.searchPatientsOfDoctor(eq(doctorId), any(), any(Pageable.class)))
                .thenReturn(empty(PageRequest.of(0, 10)));

        // a doctor passing someone else's doctorId must still be scoped to self
        patientService.list(null, UUID.randomUUID(), 1, 10, doctor);

        verify(userRepository).searchPatientsOfDoctor(eq(doctorId), any(), any(Pageable.class));
        verify(userRepository, never()).search(any(), any(), any());
    }

    @Test
    void doctorCannotReadUnassignedPatient() {
        User patient = newUser("P", "p@x.com", Role.PATIENT);
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctorId, patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> patientService.get(patient.getId(), doctor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctorCannotUpdateUnassignedPatient() {
        User patient = newUser("P", "p@x.com", Role.PATIENT);
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctorId, patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> patientService.update(patient.getId(),
                new UpdatePatientRequest("X Y", null, null, null, null, null, null, null), doctor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonPatientIdIs404EvenForAdmin() {
        User someDoctor = newUser("D", "d@x.com", Role.DOCTOR);
        when(userRepository.findById(someDoctor.getId())).thenReturn(Optional.of(someDoctor));

        assertThatThrownBy(() -> patientService.get(someDoctor.getId(), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void doctorCreatingPatientIsAutoAssignedToThemselves() {
        User doctorUser = newUserWithId(doctorId, "Doc", "doc@x.com", Role.DOCTOR);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctorUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        var detail = patientService.create(request("New Patient", "np@x.com", "Passw0rd!", null), doctor);

        verify(doctorPatientRepository).save(any(DoctorPatient.class));
        assertThat(detail.tempPassword()).isNull();
    }

    @Test
    void doctorAssigningAnotherDoctorIsRejected() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        assertThatThrownBy(() -> patientService.create(
                request("New Patient", "np@x.com", "Passw0rd!", UUID.randomUUID()), doctor))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void missingPasswordGeneratesTempPasswordMeetingPolicy() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        var detail = patientService.create(request("New Patient", "np@x.com", null, null), admin);

        assertThat(detail.tempPassword()).isNotBlank().hasSizeGreaterThanOrEqualTo(8).containsPattern("\\d");
    }

    @Test
    void adminAssignedDoctorMustBeADoctor() {
        User patientNotDoctor = newUser("P", "p2@x.com", Role.PATIENT);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.findById(patientNotDoctor.getId())).thenReturn(Optional.of(patientNotDoctor));

        assertThatThrownBy(() -> patientService.create(
                request("New Patient", "np@x.com", "Passw0rd!", patientNotDoctor.getId()), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void promoteExistingUser_switchesRoleAndCreatesProfileOnSameRow() {
        User leadUser = newUser("Signup Lead", "lead@x.com", Role.LEAD);
        when(userRepository.findById(leadUser.getId())).thenReturn(Optional.of(leadUser));

        var detail = patientService.promoteExistingUser(leadUser.getId(),
                new CreatePatientRequest("Signup Lead", "lead@x.com", null, "9000000000", null,
                        Gender.MALE, null, null, null, null, null),
                admin);

        assertThat(leadUser.getRole()).isEqualTo(Role.PATIENT);
        assertThat(detail.id()).isEqualTo(leadUser.getId().toString());
        assertThat(detail.tempPassword()).isNull();
        verify(patientProfileRepository).save(any(PatientProfile.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void promoteExistingUser_nonLeadUserIs404() {
        User patient = newUser("P", "p@x.com", Role.PATIENT);
        when(userRepository.findById(patient.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.promoteExistingUser(patient.getId(),
                request("P", "p@x.com", "Passw0rd!", null), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void updateCreatesProfileLazilyForLegacyPatients() {
        User patient = newUser("P", "p@x.com", Role.PATIENT);
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(patientProfileRepository.findByUserId(patient.getId())).thenReturn(Optional.empty());
        when(healthRecordRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId()))
                .thenReturn(List.of());

        var detail = patientService.update(patient.getId(),
                new UpdatePatientRequest(null, null, null, Gender.FEMALE, "O+", null, null, null), admin);

        verify(patientProfileRepository).save(any(PatientProfile.class));
        assertThat(detail.gender()).isEqualTo(Gender.FEMALE);
        assertThat(detail.bloodGroup()).isEqualTo("O+");
    }

    @Test
    void statsComputeAveragesFromLatestRecordsAndProfileHeights() {
        User p1 = newUser("P1", "p1@x.com", Role.PATIENT);
        User p2 = newUser("P2", "p2@x.com", Role.PATIENT);
        setCreatedAt(p1, Instant.parse("2020-01-01T00:00:00Z"));
        setCreatedAt(p2, Instant.parse("2020-01-01T00:00:00Z"));
        when(userRepository.search(eq(Role.PATIENT), any(), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(p1, p2)));

        // p1: 80kg @ 180cm -> BMI 24.7; p2: 60kg @ 150cm -> BMI 26.7 ; avg 25.7
        when(healthRecordRepository.findLatestPerPatient()).thenReturn(List.of(
                record(p1, "80.0", "22.0"),
                record(p2, "60.0", "30.0")));
        when(patientProfileRepository.findByUserId(p1.getId()))
                .thenReturn(Optional.of(profile(p1, "180.0")));
        when(patientProfileRepository.findByUserId(p2.getId()))
                .thenReturn(Optional.of(profile(p2, "150.0")));

        PatientStatsDto stats = patientService.stats(admin);

        assertThat(stats.totalPatients()).isEqualTo(2);
        assertThat(stats.averageBmi()).isEqualByComparingTo("25.7");
        assertThat(stats.averageBodyFatPct()).isEqualByComparingTo("26.0");
    }

    @Test
    void statsForDoctorOnlyCoverAssignedPatients() {
        when(userRepository.findPatientIdsOfDoctor(doctorId)).thenReturn(List.of());
        PatientStatsDto stats = patientService.stats(doctor);
        assertThat(stats.totalPatients()).isZero();
        assertThat(stats.averageBmi()).isNull();
    }

    // ---- fixtures ----------------------------------------------------------

    private CreatePatientRequest request(String name, String email, String password, UUID doctorId) {
        return new CreatePatientRequest(name, email, password, null, null, null, null, null, null, null, doctorId);
    }

    private HealthRecord record(User patient, String weight, String fat) {
        HealthRecord hr = new HealthRecord();
        hr.setPatient(patient);
        hr.setWeightKg(new BigDecimal(weight));
        hr.setBodyFatPct(new BigDecimal(fat));
        hr.setRecordedAt(Instant.now());
        return hr;
    }

    private PatientProfile profile(User patient, String heightCm) {
        PatientProfile p = new PatientProfile();
        p.setUser(patient);
        p.setHeightCm(new BigDecimal(heightCm));
        return p;
    }

    private User newUser(String name, String email, Role role) {
        return newUserWithId(UUID.randomUUID(), name, email, role);
    }

    private User newUserWithId(UUID id, String name, String email, Role role) {
        User user = new User();
        setField(user, "id", id);
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash("unset");
        return user;
    }

    private User withId(User user) {
        if (user.getId() == null) {
            setField(user, "id", UUID.randomUUID());
        }
        return user;
    }

    private void setCreatedAt(User user, Instant instant) {
        setField(user, "createdAt", instant);
    }

    private static void setField(User user, String field, Object value) {
        try {
            Field f = User.class.getSuperclass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(user, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
