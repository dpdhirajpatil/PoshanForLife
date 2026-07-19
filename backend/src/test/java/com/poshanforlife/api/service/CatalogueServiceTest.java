package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateCatalogueItemRequest;
import com.poshanforlife.api.dto.UpdateCatalogueItemRequest;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.CatalogueServiceCode;
import com.poshanforlife.api.entity.CatalogueStatus;
import com.poshanforlife.api.entity.PatientProgrammeStatus;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.Session;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.repository.CatalogueServiceCodeRepository;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.PatientProgrammeRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.SessionRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogueServiceTest {

    @Mock
    private ProgrammeRepository programmeRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private CatalogueServiceCodeRepository serviceCodeRepository;
    @Mock
    private PatientProgrammeRepository patientProgrammeRepository;
    @Mock
    private UserRepository userRepository;

    private CatalogueService catalogueService;

    private User admin;
    private AuthenticatedUser caller;

    @BeforeEach
    void setUp() {
        catalogueService = new CatalogueService(programmeRepository, sessionRepository,
                challengeRepository, serviceCodeRepository, patientProgrammeRepository,
                userRepository);
        admin = newUser("Admin", Role.ADMIN);
        caller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);
    }

    @Test
    void createProgrammeWithoutDurationWeeksFailsValidation() {
        assertThatThrownBy(() -> catalogueService.create(CatalogueItemType.PROGRAMME,
                request("Fat loss 12wk", "PRG-001", null, null, null, null), caller))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(details(ex)).containsKey("durationWeeks");
                });
        verify(programmeRepository, never()).save(any());
    }

    @Test
    void createChallengeRequiresGoalDescription() {
        assertThatThrownBy(() -> catalogueService.create(CatalogueItemType.CHALLENGE,
                request("30-day steps", "CHL-001", null, null, 30, " "), caller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(details(ex)).containsKey("goalDescription"));
    }

    @Test
    void createRejectsServiceCodeUsedByAnotherType() {
        CatalogueServiceCode taken = codeEntry("prg-001", CatalogueItemType.SESSION, UUID.randomUUID());
        when(serviceCodeRepository.findByCodeIgnoreCase("PRG-001")).thenReturn(Optional.of(taken));

        assertThatThrownBy(() -> catalogueService.create(CatalogueItemType.PROGRAMME,
                request("Fat loss 12wk", "PRG-001", 12, null, null, null), caller))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(details(ex)).containsKey("serviceCode");
                });
        verify(programmeRepository, never()).save(any());
    }

    @Test
    void createSessionSavesItemAndRegistersCode() {
        when(serviceCodeRepository.findByCodeIgnoreCase("SES-001")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(admin.getId())).thenReturn(admin);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            setId(s, UUID.randomUUID());
            return s;
        });
        when(serviceCodeRepository.saveAndFlush(any(CatalogueServiceCode.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var dto = catalogueService.create(CatalogueItemType.SESSION,
                new CreateCatalogueItemRequest("Yoga 1:1", "SES-001", "Yoga",
                        new BigDecimal("999.00"), null, null, null, null, 45, null, null),
                caller);

        assertThat(dto.status()).isEqualTo(CatalogueStatus.DRAFT);
        assertThat(dto.durationMinutes()).isEqualTo(45);
        assertThat(dto.createdBy().name()).isEqualTo("Admin");
        ArgumentCaptor<CatalogueServiceCode> registered =
                ArgumentCaptor.forClass(CatalogueServiceCode.class);
        verify(serviceCodeRepository).saveAndFlush(registered.capture());
        assertThat(registered.getValue().getCode()).isEqualTo("SES-001");
        assertThat(registered.getValue().getItemType()).isEqualTo(CatalogueItemType.SESSION);
    }

    @Test
    void updateAllowsKeepingOwnServiceCode() {
        Programme programme = programme("PRG-001", CatalogueStatus.PUBLISHED);
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        when(serviceCodeRepository.findByCodeIgnoreCase("PRG-001"))
                .thenReturn(Optional.of(codeEntry("PRG-001", CatalogueItemType.PROGRAMME, programme.getId())));
        when(serviceCodeRepository.findByItemTypeAndItemId(CatalogueItemType.PROGRAMME, programme.getId()))
                .thenReturn(Optional.of(codeEntry("PRG-001", CatalogueItemType.PROGRAMME, programme.getId())));

        var dto = catalogueService.update(CatalogueItemType.PROGRAMME, programme.getId(),
                new UpdateCatalogueItemRequest(null, "PRG-001", null, null, null, null,
                        CatalogueStatus.ARCHIVED, null, null, null, null));

        assertThat(dto.status()).isEqualTo(CatalogueStatus.ARCHIVED);
        assertThat(dto.serviceCode()).isEqualTo("PRG-001");
    }

    @Test
    void deleteBlockedWhileActiveAssignmentsExist() {
        Programme programme = programme("PRG-001", CatalogueStatus.PUBLISHED);
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        when(patientProgrammeRepository.countByItemTypeAndItemIdAndStatus(
                CatalogueItemType.PROGRAMME, programme.getId(), PatientProgrammeStatus.ACTIVE))
                .thenReturn(3L);

        assertThatThrownBy(() -> catalogueService.delete(CatalogueItemType.PROGRAMME, programme.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).contains("3 active patient assignment");
                });
        verify(programmeRepository, never()).delete(any());
    }

    @Test
    void deleteAllowedWhenArchivedDespiteActiveAssignments() {
        Programme programme = programme("PRG-001", CatalogueStatus.ARCHIVED);
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        when(patientProgrammeRepository.countByItemTypeAndItemIdAndStatus(
                CatalogueItemType.PROGRAMME, programme.getId(), PatientProgrammeStatus.ACTIVE))
                .thenReturn(3L);
        when(serviceCodeRepository.findByItemTypeAndItemId(CatalogueItemType.PROGRAMME, programme.getId()))
                .thenReturn(Optional.of(codeEntry("PRG-001", CatalogueItemType.PROGRAMME, programme.getId())));

        catalogueService.delete(CatalogueItemType.PROGRAMME, programme.getId());

        verify(programmeRepository).delete(programme);
        verify(serviceCodeRepository).delete(any(CatalogueServiceCode.class));
    }

    @Test
    void listRejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> catalogueService.list(CatalogueItemType.SESSION, "live", null, 1, 10))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ---- fixtures ----------------------------------------------------------

    private CreateCatalogueItemRequest request(String name, String code, Integer weeks,
                                               Integer minutes, Integer days, String goal) {
        return new CreateCatalogueItemRequest(name, code, "General", new BigDecimal("4999.00"),
                null, null, null, weeks, minutes, days, goal);
    }

    private Programme programme(String code, CatalogueStatus status) {
        Programme programme = new Programme();
        setId(programme, UUID.randomUUID());
        programme.setName("Fat loss 12wk");
        programme.setServiceCode(code);
        programme.setType("Weight loss");
        programme.setPriceInr(new BigDecimal("4999.00"));
        programme.setStatus(status);
        programme.setDurationWeeks(12);
        programme.setCreatedBy(admin);
        return programme;
    }

    private CatalogueServiceCode codeEntry(String code, CatalogueItemType type, UUID itemId) {
        CatalogueServiceCode entry = new CatalogueServiceCode();
        entry.setCode(code);
        entry.setItemType(type);
        entry.setItemId(itemId);
        return entry;
    }

    private User newUser(String name, Role role) {
        User user = new User();
        setId(user, UUID.randomUUID());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@poshan.test");
        user.setRole(role);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> details(ApiException ex) {
        return (Map<String, String>) ex.getDetails();
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, id);
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
