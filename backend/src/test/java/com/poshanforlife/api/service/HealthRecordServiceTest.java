package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.dto.UpsertHealthRecordRequest;
import com.poshanforlife.api.dto.UpsertHealthRecordResponseDto;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.HealthRecordSource;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthRecordServiceTest {

    @Mock
    private HealthRecordRepository healthRecordRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;

    private HealthRecordService healthRecordService;

    private User doctor;
    private User patient;
    private User otherPatient;
    private User lead;
    private AuthenticatedUser doctorCaller;
    private AuthenticatedUser patientCaller;
    private AuthenticatedUser leadCaller;
    private AuthenticatedUser adminCaller;

    /** Backs the dynamic save/find stubs below so upsert()'s own re-read of the saved row works in tests. */
    private final List<HealthRecord> savedRecords = new ArrayList<>();

    @BeforeEach
    void setUp() {
        healthRecordService = new HealthRecordService(healthRecordRepository, userRepository,
                doctorPatientRepository, patientProfileRepository);

        doctor = newUser("Dr Priya", Role.DOCTOR);
        patient = newUser("Pat Kumar", Role.PATIENT);
        otherPatient = newUser("Someone Else", Role.PATIENT);
        lead = newUser("Prospective Lead", Role.LEAD);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), "doctor@poshan.test", Role.DOCTOR);
        patientCaller = new AuthenticatedUser(patient.getId().toString(), "patient@poshan.test", Role.PATIENT);
        leadCaller = new AuthenticatedUser(lead.getId().toString(), "lead@poshan.test", Role.LEAD);
        adminCaller = new AuthenticatedUser(UUID.randomUUID().toString(), "admin@poshan.test", Role.ADMIN);

        lenient().when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        lenient().when(userRepository.findById(otherPatient.getId())).thenReturn(Optional.of(otherPatient));
        lenient().when(userRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        lenient().when(patientProfileRepository.findByUserId(any())).thenReturn(Optional.empty());

        lenient().when(healthRecordRepository.findByPatientIdAndRecordDate(any(), any())).thenAnswer(inv -> {
            UUID patientId = inv.getArgument(0);
            LocalDate date = inv.getArgument(1);
            return savedRecords.stream()
                    .filter(r -> r.getPatient().getId().equals(patientId) && r.getRecordDate().equals(date))
                    .findFirst();
        });
        lenient().when(healthRecordRepository.save(any())).thenAnswer(inv -> {
            HealthRecord r = withId(inv.getArgument(0));
            savedRecords.removeIf(existing -> existing.getPatient().getId().equals(r.getPatient().getId())
                    && existing.getRecordDate().equals(r.getRecordDate()));
            savedRecords.add(r);
            return r;
        });
        lenient().when(healthRecordRepository.findByPatientIdOrderByRecordDateAsc(any())).thenAnswer(inv -> {
            UUID patientId = inv.getArgument(0);
            return savedRecords.stream()
                    .filter(r -> r.getPatient().getId().equals(patientId))
                    .sorted(Comparator.comparing(HealthRecord::getRecordDate))
                    .toList();
        });
    }

    @Test
    void patientCanReadOwnHealthRecords() {
        HealthRecord record = newRecord(patient);
        when(healthRecordRepository.findByPatientIdOrderByRecordDateAsc(patient.getId()))
                .thenReturn(List.of(record));

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), patientCaller, null, null, Set.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(record.getId().toString());
    }

    @Test
    void leadCanReadOwnHealthRecords() {
        HealthRecord record = newRecord(lead);
        when(healthRecordRepository.findByPatientIdOrderByRecordDateAsc(lead.getId()))
                .thenReturn(List.of(record));

        List<HealthRecordDto> result = healthRecordService.list(lead.getId(), leadCaller, null, null, Set.of());

        assertThat(result).hasSize(1);
    }

    @Test
    void patientCannotReadAnotherPatientsHealthRecords_returns404NotForbidden() {
        assertThatThrownBy(() -> healthRecordService.list(otherPatient.getId(), patientCaller, null, null, Set.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctorCannotReadUnassignedPatientsHealthRecords_returns403() {
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> healthRecordService.list(patient.getId(), doctorCaller, null, null, Set.of()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctorCanReadAssignedPatientsHealthRecords() {
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(true);
        when(healthRecordRepository.findByPatientIdOrderByRecordDateAsc(patient.getId()))
                .thenReturn(List.of());

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), doctorCaller, null, null, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void bmiComputedFromProfileHeightWhenAvailable() {
        HealthRecord record = newRecord(patient);
        record.setWeightKg(new BigDecimal("70.0"));
        PatientProfile profile = new PatientProfile();
        profile.setHeightCm(new BigDecimal("175"));
        when(patientProfileRepository.findByUserId(patient.getId())).thenReturn(Optional.of(profile));
        when(healthRecordRepository.findByPatientIdOrderByRecordDateAsc(patient.getId()))
                .thenReturn(List.of(record));

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), patientCaller, null, null, Set.of());

        assertThat(result.get(0).bmi()).isEqualByComparingTo(new BigDecimal("22.9"));
    }

    @Test
    void deltaIsComputedAgainstImmediatelyPreviousRecord() {
        HealthRecord day1 = newRecord(patient);
        day1.setRecordDate(LocalDate.of(2026, 1, 1));
        day1.setWeightKg(new BigDecimal("70.0"));
        HealthRecord day2 = newRecord(patient);
        day2.setRecordDate(LocalDate.of(2026, 1, 2));
        day2.setWeightKg(new BigDecimal("69.5"));
        when(healthRecordRepository.findByPatientIdOrderByRecordDateAsc(patient.getId()))
                .thenReturn(List.of(day1, day2));

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), patientCaller, null, null, Set.of());

        assertThat(result.get(0).weightKgDelta()).isNull();
        assertThat(result.get(1).weightKgDelta()).isEqualByComparingTo(new BigDecimal("-0.5"));
    }

    @Test
    void patientSelfWriteDefaultsToOwnIdAndForcesAllowedSource() {
        UpsertHealthRecordRequest request = new UpsertHealthRecordRequest(
                null, null, HealthRecordSource.WEARABLE_SYNC, new BigDecimal("68.0"),
                null, null, null, null, null, null, null, null);

        UpsertHealthRecordResponseDto result = healthRecordService.upsert(request, patientCaller);

        assertThat(result.upserted()).isFalse();
        assertThat(result.record().source()).isEqualTo(HealthRecordSource.WEARABLE_SYNC);
    }

    @Test
    void secondUpsertOnSameDayMergesAndReportsUpserted() {
        UpsertHealthRecordRequest first = new UpsertHealthRecordRequest(
                null, LocalDate.of(2026, 1, 5), HealthRecordSource.PATIENT_MANUAL, new BigDecimal("68.0"),
                null, null, null, null, null, null, null, null);
        healthRecordService.upsert(first, patientCaller);

        UpsertHealthRecordRequest second = new UpsertHealthRecordRequest(
                null, LocalDate.of(2026, 1, 5), HealthRecordSource.WEARABLE_SYNC, new BigDecimal("67.5"),
                null, null, null, null, null, null, null, null);
        UpsertHealthRecordResponseDto result = healthRecordService.upsert(second, patientCaller);

        assertThat(result.upserted()).isTrue();
        assertThat(result.record().weightKg()).isEqualByComparingTo(new BigDecimal("67.5"));
        assertThat(result.record().source()).isEqualTo(HealthRecordSource.WEARABLE_SYNC);
    }

    @Test
    void patientCannotWriteAnotherPatientsRecord() {
        UpsertHealthRecordRequest request = new UpsertHealthRecordRequest(
                otherPatient.getId().toString(), null, HealthRecordSource.PATIENT_MANUAL, new BigDecimal("68.0"),
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> healthRecordService.upsert(request, patientCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void patientMustUseAllowedSource() {
        UpsertHealthRecordRequest request = new UpsertHealthRecordRequest(
                null, null, HealthRecordSource.MANUAL, new BigDecimal("68.0"),
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> healthRecordService.upsert(request, patientCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void adminMustSupplyPatientIdAndSourceIsForcedToManual() {
        UpsertHealthRecordRequest missingPatientId = new UpsertHealthRecordRequest(
                null, null, null, new BigDecimal("68.0"), null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> healthRecordService.upsert(missingPatientId, adminCaller))
                .isInstanceOf(ApiException.class);

        UpsertHealthRecordRequest valid = new UpsertHealthRecordRequest(
                patient.getId().toString(), null, HealthRecordSource.PATIENT_MANUAL, new BigDecimal("68.0"),
                null, null, null, null, null, null, null, null);

        UpsertHealthRecordResponseDto result = healthRecordService.upsert(valid, adminCaller);

        // Forced to MANUAL regardless of the PATIENT_MANUAL the caller sent — ADMIN/DOCTOR can't self-attribute a patient source.
        assertThat(result.record().source()).isEqualTo(HealthRecordSource.MANUAL);
    }

    @Test
    void deleteRejectsRecordBelongingToAnotherPatient() {
        HealthRecord record = newRecord(otherPatient);
        when(healthRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> healthRecordService.delete(patient.getId(), record.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static HealthRecord newRecord(User patient) {
        HealthRecord record = withId(new HealthRecord());
        record.setPatient(patient);
        record.setWeightKg(new BigDecimal("68.0"));
        record.setBodyFatPct(new BigDecimal("20.0"));
        record.setRecordedAt(Instant.now());
        record.setRecordDate(LocalDate.now());
        return record;
    }

    private static User newUser(String name, Role role) {
        User user = withId(new User());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@poshan.test");
        user.setRole(role);
        return user;
    }

    private static <T> T withId(T entity) {
        try {
            Field field = findIdField(entity.getClass());
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, UUID.randomUUID());
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("id");
    }
}
