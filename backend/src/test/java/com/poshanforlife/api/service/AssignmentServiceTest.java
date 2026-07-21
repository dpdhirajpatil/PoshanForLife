package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateAssignmentRequest;
import com.poshanforlife.api.entity.DoctorPatient;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private AssignmentService assignmentService;

    private User doctor;
    private User patient;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(doctorPatientRepository, userRepository, notificationService);
        doctor = newUser("Dr. Jones", Role.DOCTOR);
        patient = newUser("Pat Kumar", Role.PATIENT);
    }

    @Test
    void createSavesLinkAndNotifiesDoctor() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);
        when(doctorPatientRepository.save(any(DoctorPatient.class))).thenAnswer(inv -> {
            DoctorPatient dp = inv.getArgument(0);
            setField(dp, UUID.randomUUID());
            return dp;
        });

        var dto = assignmentService.create(new CreateAssignmentRequest(doctor.getId(), patient.getId()));

        assertThat(dto.doctor().name()).isEqualTo("Dr. Jones");
        assertThat(dto.patient().name()).isEqualTo("Pat Kumar");
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(doctor), eq(Notification.TYPE_PATIENT_ASSIGNED), any(),
                messageCaptor.capture(), eq("patient"), eq(patient.getId()));
        assertThat(messageCaptor.getValue()).contains("Pat Kumar");
    }

    @Test
    void duplicatePairIs409AssignmentConflict() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> assignmentService.create(
                new CreateAssignmentRequest(doctor.getId(), patient.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ASSIGNMENT_CONFLICT));
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void wrongRolesAreRejected() {
        // doctorId pointing at a patient
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> assignmentService.create(
                new CreateAssignmentRequest(patient.getId(), patient.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void deleteMissingAssignmentIs404() {
        UUID id = UUID.randomUUID();
        when(doctorPatientRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> assignmentService.delete(id))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void listFiltersByPatientId() {
        DoctorPatient dp = new DoctorPatient();
        setField(dp, UUID.randomUUID());
        dp.setDoctor(doctor);
        dp.setPatient(patient);
        when(doctorPatientRepository.findByPatientId(patient.getId())).thenReturn(java.util.List.of(dp));

        var result = assignmentService.list(null, patient.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().patient().name()).isEqualTo("Pat Kumar");
    }

    private static User newUser(String name, Role role) {
        User user = new User();
        try {
            Field f = User.class.getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@x.com");
        user.setRole(role);
        user.setPasswordHash("unset");
        return user;
    }

    private static void setField(DoctorPatient dp, UUID id) {
        try {
            Field f = DoctorPatient.class.getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(dp, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
