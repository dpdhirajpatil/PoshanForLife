package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
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
import java.util.List;
import java.util.Optional;
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
    private AuthenticatedUser doctorCaller;
    private AuthenticatedUser patientCaller;

    @BeforeEach
    void setUp() {
        healthRecordService = new HealthRecordService(healthRecordRepository, userRepository,
                doctorPatientRepository, patientProfileRepository);

        doctor = newUser("Dr Priya", Role.DOCTOR);
        patient = newUser("Pat Kumar", Role.PATIENT);
        otherPatient = newUser("Someone Else", Role.PATIENT);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), "doctor@poshan.test", Role.DOCTOR);
        patientCaller = new AuthenticatedUser(patient.getId().toString(), "patient@poshan.test", Role.PATIENT);

        lenient().when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        lenient().when(userRepository.findById(otherPatient.getId())).thenReturn(Optional.of(otherPatient));
        lenient().when(patientProfileRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    @Test
    void patientCanReadOwnHealthRecords() {
        HealthRecord record = newRecord(patient);
        when(healthRecordRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId()))
                .thenReturn(List.of(record));

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), patientCaller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(record.getId().toString());
    }

    @Test
    void patientCannotReadAnotherPatientsHealthRecords_returns404NotForbidden() {
        assertThatThrownBy(() -> healthRecordService.list(otherPatient.getId(), patientCaller))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctorCannotReadUnassignedPatientsHealthRecords_returns403() {
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> healthRecordService.list(patient.getId(), doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctorCanReadAssignedPatientsHealthRecords() {
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(true);
        when(healthRecordRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId()))
                .thenReturn(List.of());

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), doctorCaller);

        assertThat(result).isEmpty();
    }

    @Test
    void bmiComputedFromProfileHeightWhenAvailable() {
        HealthRecord record = newRecord(patient);
        record.setWeightKg(new BigDecimal("70.0"));
        PatientProfile profile = new PatientProfile();
        profile.setHeightCm(new BigDecimal("175"));
        when(patientProfileRepository.findByUserId(patient.getId())).thenReturn(Optional.of(profile));
        when(healthRecordRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId()))
                .thenReturn(List.of(record));

        List<HealthRecordDto> result = healthRecordService.list(patient.getId(), patientCaller);

        assertThat(result.get(0).bmi()).isEqualByComparingTo(new BigDecimal("22.9"));
    }

    private static HealthRecord newRecord(User patient) {
        HealthRecord record = withId(new HealthRecord());
        record.setPatient(patient);
        record.setWeightKg(new BigDecimal("68.0"));
        record.setBodyFatPct(new BigDecimal("20.0"));
        record.setRecordedAt(Instant.now());
        record.setRecordDate(java.time.LocalDate.now());
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
            field.set(entity, UUID.randomUUID());
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
