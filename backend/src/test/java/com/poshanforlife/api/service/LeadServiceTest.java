package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.ConvertLeadRequest;
import com.poshanforlife.api.dto.ConvertLeadResponseDto;
import com.poshanforlife.api.dto.CreateLeadActivityRequest;
import com.poshanforlife.api.dto.CreateLeadRequest;
import com.poshanforlife.api.dto.LeadDetailDto;
import com.poshanforlife.api.dto.PatientDetailDto;
import com.poshanforlife.api.dto.RequestConsultationRequest;
import com.poshanforlife.api.dto.ScheduleFollowupRequest;
import com.poshanforlife.api.dto.UpdateLeadRequest;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.LeadActivity;
import com.poshanforlife.api.entity.LeadActivityType;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.repository.LeadActivityRepository;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadActivityRepository leadActivityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProgrammeRepository programmeRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PatientService patientService;
    @Mock
    private PatientProgrammeService patientProgrammeService;

    private LeadService leadService;

    private User admin;
    private User doctor;
    private User otherDoctor;
    private AuthenticatedUser adminCaller;
    private AuthenticatedUser doctorCaller;

    @BeforeEach
    void setUp() {
        leadService = new LeadService(leadRepository, leadActivityRepository, userRepository,
                programmeRepository, notificationService, patientService, patientProgrammeService);

        admin = newUser("Admin", Role.ADMIN);
        doctor = newUser("Dr Priya", Role.DOCTOR);
        otherDoctor = newUser("Dr Arjun", Role.DOCTOR);
        adminCaller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), "doctor@poshan.test", Role.DOCTOR);

        lenient().when(userRepository.getReferenceById(admin.getId())).thenReturn(admin);
        lenient().when(userRepository.getReferenceById(doctor.getId())).thenReturn(doctor);
        lenient().when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(leadActivityRepository.save(any(LeadActivity.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(leadActivityRepository.findByLeadIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
    }

    @Test
    void create_doctorCallerAutoAssignsSelf() {
        CreateLeadRequest request = new CreateLeadRequest("Neha Kapoor", "9000000001", null, null, null,
                null, LeadSource.WEBSITE, null, null, null, null, null);

        LeadDetailDto dto = leadService.create(request, doctorCaller);

        assertThat(dto.assignedPractitioner().id()).isEqualTo(doctor.getId().toString());
        assertThat(dto.stage()).isEqualTo(LeadStage.NEW);
    }

    @Test
    void create_doctorCannotAssignToAnotherDoctor() {
        CreateLeadRequest request = new CreateLeadRequest("Neha Kapoor", null, null, null, null, null,
                LeadSource.WEBSITE, null, null, otherDoctor.getId(), null, null);

        assertThatThrownBy(() -> leadService.create(request, doctorCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void create_adminAssignsSpecificPractitioner() {
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        CreateLeadRequest request = new CreateLeadRequest("Neha Kapoor", null, null, null, null, null,
                LeadSource.REFERRAL, null, null, doctor.getId(), null, null);

        LeadDetailDto dto = leadService.create(request, adminCaller);

        assertThat(dto.assignedPractitioner().id()).isEqualTo(doctor.getId().toString());
    }

    @Test
    void get_doctorDeniedForUnassignedLead() {
        Lead lead = existingLead(null);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> leadService.get(lead.getId(), doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_doctorAllowedForOwnAssignedLead() {
        Lead lead = existingLead(doctor);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        LeadDetailDto dto = leadService.get(lead.getId(), doctorCaller);

        assertThat(dto.id()).isEqualTo(lead.getId().toString());
    }

    @Test
    void update_stageChangeAutoCreatesStageChangeActivity() {
        Lead lead = existingLead(admin);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        UpdateLeadRequest request = new UpdateLeadRequest(null, null, null, null, null, null, null, null,
                LeadStage.CONTACTED, null, null, null);

        leadService.update(lead.getId(), request, adminCaller);

        assertThat(lead.getStage()).isEqualTo(LeadStage.CONTACTED);
        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(leadActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(LeadActivityType.STAGE_CHANGE);
        assertThat(captor.getValue().getOldStage()).isEqualTo(LeadStage.NEW);
        assertThat(captor.getValue().getNewStage()).isEqualTo(LeadStage.CONTACTED);
    }

    @Test
    void update_nonStageFieldsDoNotCreateActivity() {
        Lead lead = existingLead(admin);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        UpdateLeadRequest request = new UpdateLeadRequest("New Name", null, null, null, null, null, null,
                null, null, null, null, null);

        leadService.update(lead.getId(), request, adminCaller);

        assertThat(lead.getName()).isEqualTo("New Name");
        verify(leadActivityRepository, never()).save(any());
    }

    @Test
    void addActivity_appendsToTimeline() {
        Lead lead = existingLead(admin);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        CreateLeadActivityRequest request = new CreateLeadActivityRequest(LeadActivityType.CALL, "Called the lead");

        leadService.addActivity(lead.getId(), request, adminCaller);

        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(leadActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(LeadActivityType.CALL);
        assertThat(captor.getValue().getDescription()).isEqualTo("Called the lead");
    }

    @Test
    void convert_alreadyConvertedThrows() {
        Lead lead = existingLead(admin);
        lead.setStage(LeadStage.CONVERTED);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> leadService.convert(lead.getId(), emptyConvertRequest(), adminCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void convert_missingEmailThrows() {
        Lead lead = existingLead(admin);
        lead.setEmail(null);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> leadService.convert(lead.getId(), emptyConvertRequest(), adminCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void convert_success_delegatesToPatientServiceAndSetsConvertedStage() {
        Lead lead = existingLead(doctor);
        lead.setEmail("lead@example.com");
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        User createdPatient = newUser("New Patient", Role.PATIENT);
        when(patientService.create(any(), eq(adminCaller)))
                .thenReturn(patientDetail(createdPatient.getId()));
        when(userRepository.getReferenceById(createdPatient.getId())).thenReturn(createdPatient);

        ConvertLeadResponseDto result = leadService.convert(lead.getId(), emptyConvertRequest(), adminCaller);

        assertThat(result.patientId()).isEqualTo(createdPatient.getId().toString());
        assertThat(lead.getStage()).isEqualTo(LeadStage.CONVERTED);
        assertThat(lead.getConvertedPatient()).isEqualTo(createdPatient);
        verify(patientProgrammeService, never()).create(any(), any(), any());
        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(leadActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(LeadActivityType.CONVERTED);
    }

    @Test
    void convert_selfSignupLead_promotesExistingUserInsteadOfCreatingNew() {
        User leadUser = newUser("Signup Lead", Role.LEAD);
        Lead lead = existingLead(null);
        lead.setEmail("lead@example.com");
        lead.setConvertedPatient(leadUser);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(patientService.promoteExistingUser(eq(leadUser.getId()), any(), eq(adminCaller)))
                .thenReturn(patientDetail(leadUser.getId()));
        when(userRepository.getReferenceById(leadUser.getId())).thenReturn(leadUser);

        ConvertLeadResponseDto result = leadService.convert(lead.getId(), emptyConvertRequest(), adminCaller);

        assertThat(result.patientId()).isEqualTo(leadUser.getId().toString());
        assertThat(lead.getStage()).isEqualTo(LeadStage.CONVERTED);
        assertThat(lead.getConvertedPatient()).isEqualTo(leadUser);
        verify(patientService, never()).create(any(), any());
    }

    @Test
    void requestConsultation_notifiesAssignedPractitioner() {
        User leadUser = newUser("Signup Lead", Role.LEAD);
        Lead lead = existingLead(doctor);
        lead.setConvertedPatient(leadUser);
        when(leadRepository.findByConvertedPatientId(leadUser.getId())).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(leadUser.getId())).thenReturn(leadUser);
        AuthenticatedUser leadCaller = new AuthenticatedUser(leadUser.getId().toString(), leadUser.getEmail(), Role.LEAD);

        leadService.requestConsultation(new RequestConsultationRequest("Morning", "Please call"), leadCaller);

        verify(notificationService).create(eq(doctor), eq(Notification.TYPE_CONSULTATION_REQUEST),
                any(), any(), eq("lead"), eq(lead.getId()));
        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(leadActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(LeadActivityType.NOTE);
        assertThat(captor.getValue().getDescription()).contains("Morning").contains("Please call");
    }

    @Test
    void requestConsultation_noAssignedPractitioner_notifiesAllActiveAdmins() {
        User leadUser = newUser("Signup Lead", Role.LEAD);
        Lead lead = existingLead(null);
        lead.setConvertedPatient(leadUser);
        when(leadRepository.findByConvertedPatientId(leadUser.getId())).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(leadUser.getId())).thenReturn(leadUser);
        when(userRepository.findByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));
        AuthenticatedUser leadCaller = new AuthenticatedUser(leadUser.getId().toString(), leadUser.getEmail(), Role.LEAD);

        leadService.requestConsultation(new RequestConsultationRequest(null, null), leadCaller);

        verify(notificationService).create(eq(admin), eq(Notification.TYPE_CONSULTATION_REQUEST),
                any(), any(), eq("lead"), eq(lead.getId()));
    }

    @Test
    void requestConsultation_noLinkedLead_throwsNotFound() {
        UUID callerId = UUID.randomUUID();
        when(leadRepository.findByConvertedPatientId(callerId)).thenReturn(Optional.empty());
        AuthenticatedUser leadCaller = new AuthenticatedUser(callerId.toString(), "x@poshan.test", Role.LEAD);

        assertThatThrownBy(() -> leadService.requestConsultation(new RequestConsultationRequest(null, null), leadCaller))
                .isInstanceOf(com.poshanforlife.api.exception.ResourceNotFoundException.class);
    }

    @Test
    void convert_withServiceAssignment_delegatesToPatientProgrammeService() {
        Lead lead = existingLead(admin);
        lead.setEmail("lead@example.com");
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        User createdPatient = newUser("New Patient", Role.PATIENT);
        when(patientService.create(any(), eq(adminCaller))).thenReturn(patientDetail(createdPatient.getId()));
        when(userRepository.getReferenceById(createdPatient.getId())).thenReturn(createdPatient);
        UUID programmeId = UUID.randomUUID();

        ConvertLeadRequest request = new ConvertLeadRequest(null, null, null, null, null, null,
                programmeId, CatalogueItemType.PROGRAMME, null, null);

        leadService.convert(lead.getId(), request, adminCaller);

        verify(patientProgrammeService).create(eq(createdPatient.getId()), any(), eq(adminCaller));
    }

    @Test
    void scheduleFollowup_setsNextFollowupAtAndNotifiesPractitioner() {
        Lead lead = existingLead(doctor);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        ScheduleFollowupRequest request = new ScheduleFollowupRequest(
                OffsetDateTime.parse("2026-08-01T10:00:00+05:30"), "Call about pricing");

        leadService.scheduleFollowup(lead.getId(), request, adminCaller);

        assertThat(lead.getNextFollowupAt()).isNotNull();
        verify(notificationService).create(eq(doctor), eq(Notification.TYPE_LEAD_FOLLOWUP), any(), any(),
                eq("lead"), eq(lead.getId()));
        verify(leadActivityRepository).save(any());
    }

    @Test
    void scheduleFollowup_noPractitionerAssigned_skipsNotification() {
        Lead lead = existingLead(null);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        ScheduleFollowupRequest request = new ScheduleFollowupRequest(
                OffsetDateTime.parse("2026-08-01T10:00:00+05:30"), null);

        leadService.scheduleFollowup(lead.getId(), request, adminCaller);

        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    private ConvertLeadRequest emptyConvertRequest() {
        return new ConvertLeadRequest(null, null, null, null, null, null, null, null, null, null);
    }

    private PatientDetailDto patientDetail(UUID id) {
        return new PatientDetailDto(id.toString(), "New Patient", "lead@example.com", null, null, true,
                null, null, null, null, null, null, List.of(), List.of(), List.of(), null, null, null);
    }

    private Lead existingLead(User assignedPractitioner) {
        Lead lead = withId(new Lead());
        lead.setName("Existing Lead");
        lead.setSource(LeadSource.WEBSITE);
        lead.setStage(LeadStage.NEW);
        lead.setAssignedPractitioner(assignedPractitioner);
        lead.setCreatedBy(admin);
        return lead;
    }

    private User newUser(String name, Role role) {
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
