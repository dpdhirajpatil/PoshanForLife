package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreatePatientProgrammeRequest;
import com.poshanforlife.api.dto.PatientProgrammeDto;
import com.poshanforlife.api.dto.UpdatePatientProgrammeRequest;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.CatalogueStatus;
import com.poshanforlife.api.entity.Challenge;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PaymentStatus;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.Session;
import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.TransactionType;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.OrderRepository;
import com.poshanforlife.api.repository.PatientProgrammeRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.SessionRepository;
import com.poshanforlife.api.repository.TransactionRepository;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientProgrammeServiceTest {

    @Mock
    private PatientProgrammeRepository patientProgrammeRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ProgrammeRepository programmeRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private TransactionNumbers transactionNumbers;
    @Mock
    private BadgeEvaluationService badgeEvaluationService;

    private PatientProgrammeService service;

    private User admin;
    private User patient;
    private User otherPatient;
    private AuthenticatedUser adminCaller;
    private AuthenticatedUser patientCaller;

    @BeforeEach
    void setUp() {
        // real factory over the mocked repos so transaction assertions still apply
        TransactionFactory transactionFactory =
                new TransactionFactory(transactionRepository, transactionNumbers, userRepository);
        service = new PatientProgrammeService(patientProgrammeRepository, orderRepository,
                transactionRepository, programmeRepository, sessionRepository,
                challengeRepository, userRepository, doctorPatientRepository,
                transactionFactory, badgeEvaluationService);
        admin = newUser("Admin", Role.ADMIN);
        patient = newUser("Pat Kumar", Role.PATIENT);
        otherPatient = newUser("Someone Else", Role.PATIENT);
        adminCaller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);
        patientCaller = new AuthenticatedUser(patient.getId().toString(), "patient@poshan.test", Role.PATIENT);

        lenient().when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        lenient().when(userRepository.findById(otherPatient.getId())).thenReturn(Optional.of(otherPatient));
        lenient().when(userRepository.getReferenceById(admin.getId())).thenReturn(admin);
        lenient().when(patientProgrammeRepository.save(any(PatientProgramme.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(transactionNumbers.newTransactionId()).thenReturn("TRNID1749123456789AB2C3");
        lenient().when(transactionNumbers.nextInvoiceNumber()).thenReturn("INV-202607-0001");
    }

    @Test
    void assignProgrammeCreatesAssignmentOrderAndActivationTransaction() {
        Programme programme = publishedProgramme(12, "4999.00");
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

        LocalDate start = LocalDate.of(2026, 7, 20);
        PatientProgrammeDto dto = service.create(patient.getId(),
                request(CatalogueItemType.PROGRAMME, programme.getId(), start, null), adminCaller);

        assertThat(dto.startDate()).isEqualTo(start);
        assertThat(dto.endDate()).isEqualTo(start.plusWeeks(12));
        assertThat(dto.priceInr()).isEqualByComparingTo("4999.00");
        assertThat(dto.order()).isNotNull();
        assertThat(dto.order().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(dto.order().transactions()).hasSize(1);
        assertThat(dto.order().transactions().getFirst().invoiceNumber())
                .isEqualTo("INV-202607-0001");

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(tx.capture());
        assertThat(tx.getValue().getTransactionType()).isEqualTo(TransactionType.ACTIVATION);
        assertThat(tx.getValue().getPaymentType()).isEqualTo(PaymentType.OFFLINE);
        assertThat(tx.getValue().getAmountInr()).isEqualByComparingTo("4999.00");
    }

    @Test
    void sessionEndDateEqualsStartDate() {
        Session session = publishedSession(45, "999.00");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        LocalDate start = LocalDate.of(2026, 7, 22);
        PatientProgrammeDto dto = service.create(patient.getId(),
                request(CatalogueItemType.SESSION, session.getId(), start, null), adminCaller);

        assertThat(dto.endDate()).isEqualTo(start);
    }

    @Test
    void challengeEndDateDerivedFromDurationDays() {
        Challenge challenge = publishedChallenge(30, "0.00");
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        LocalDate start = LocalDate.of(2026, 8, 1);
        PatientProgrammeDto dto = service.create(patient.getId(),
                request(CatalogueItemType.CHALLENGE, challenge.getId(), start, null), adminCaller);

        assertThat(dto.endDate()).isEqualTo(start.plusDays(30));
    }

    @Test
    void freeItemCreatesOrderButNoTransaction() {
        Challenge challenge = publishedChallenge(30, "0.00");
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        PatientProgrammeDto dto = service.create(patient.getId(),
                request(CatalogueItemType.CHALLENGE, challenge.getId(), null, null), adminCaller);

        assertThat(dto.order()).isNotNull();
        assertThat(dto.order().transactions()).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void priceOverrideWinsOverCataloguePrice() {
        Programme programme = publishedProgramme(12, "4999.00");
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

        PatientProgrammeDto dto = service.create(patient.getId(),
                request(CatalogueItemType.PROGRAMME, programme.getId(), null,
                        new BigDecimal("2500.00")), adminCaller);

        assertThat(dto.priceInr()).isEqualByComparingTo("2500.00");
        assertThat(dto.order().amountInr()).isEqualByComparingTo("2500.00");
    }

    @Test
    void unpublishedItemIsRejected() {
        Programme draft = publishedProgramme(12, "4999.00");
        draft.setStatus(CatalogueStatus.DRAFT);
        when(programmeRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.create(patient.getId(),
                request(CatalogueItemType.PROGRAMME, draft.getId(), null, null), adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).contains("published");
                });
        verify(orderRepository, never()).save(any());
    }

    @Test
    void mismatchedItemIdFieldIsRejected() {
        assertThatThrownBy(() -> service.create(patient.getId(),
                new CreatePatientProgrammeRequest(CatalogueItemType.PROGRAMME,
                        null, UUID.randomUUID(), null, null, null, null, null),
                adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(details(ex)).containsKey("programmeId"));
    }

    @Test
    void doctorScopedToOwnPatients() {
        User doctor = newUser("Dr. Jones", Role.DOCTOR);
        AuthenticatedUser doctorCaller =
                new AuthenticatedUser(doctor.getId().toString(), "doc@poshan.test", Role.DOCTOR);
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(patient.getId(),
                request(CatalogueItemType.SESSION, UUID.randomUUID(), null, null), doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void patientCanListOwnAssignments() {
        when(patientProgrammeRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId()))
                .thenReturn(List.of());
        lenient().when(orderRepository.findByPatientProgrammeIdIn(any())).thenReturn(List.of());

        List<PatientProgrammeDto> result = service.list(patient.getId(), patientCaller);

        assertThat(result).isEmpty();
    }

    @Test
    void patientCannotListAnotherPatientsAssignments_returns404NotForbidden() {
        assertThatThrownBy(() -> service.list(otherPatient.getId(), patientCaller))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void patientCanReadOwnAssignmentDetail() {
        PatientProgramme pp = assignment();
        when(patientProgrammeRepository.findById(pp.getId())).thenReturn(Optional.of(pp));
        when(orderRepository.findByPatientProgrammeId(pp.getId())).thenReturn(Optional.empty());

        PatientProgrammeDto dto = service.get(patient.getId(), pp.getId(), patientCaller);

        assertThat(dto.id()).isEqualTo(pp.getId().toString());
    }

    @Test
    void patientCannotReadAnotherPatientsAssignmentDetail_returns404NotForbidden() {
        PatientProgramme pp = assignment();
        pp.setPatient(otherPatient);

        assertThatThrownBy(() -> service.get(otherPatient.getId(), pp.getId(), patientCaller))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteBlockedOnceNonRefundTransactionExists() {
        PatientProgramme pp = assignment();
        Order order = withId(new Order());
        when(patientProgrammeRepository.findById(pp.getId())).thenReturn(Optional.of(pp));
        when(orderRepository.findByPatientProgrammeId(pp.getId())).thenReturn(Optional.of(order));
        when(transactionRepository.existsByOrderIdAndTransactionTypeNot(
                order.getId(), TransactionType.REFUND)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(patient.getId(), pp.getId(), adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getMessage()).contains("refund"));
        verify(patientProgrammeRepository, never()).delete(any());
        verify(orderRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesAssignmentAndOrderWhenNoTransactions() {
        PatientProgramme pp = assignment();
        Order order = withId(new Order());
        when(patientProgrammeRepository.findById(pp.getId())).thenReturn(Optional.of(pp));
        when(orderRepository.findByPatientProgrammeId(pp.getId())).thenReturn(Optional.of(order));
        when(transactionRepository.existsByOrderIdAndTransactionTypeNot(
                order.getId(), TransactionType.REFUND)).thenReturn(false);
        when(transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(List.of());

        service.delete(patient.getId(), pp.getId(), adminCaller);

        verify(orderRepository).delete(order);
        verify(patientProgrammeRepository).delete(pp);
    }

    @Test
    void movingStartDateRecomputesEndDateFromDuration() {
        Programme programme = publishedProgramme(12, "4999.00");
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        PatientProgramme pp = assignment();
        pp.setProgrammeId(programme.getId());
        when(patientProgrammeRepository.findById(pp.getId())).thenReturn(Optional.of(pp));
        when(orderRepository.findByPatientProgrammeId(pp.getId())).thenReturn(Optional.empty());

        LocalDate newStart = LocalDate.of(2026, 9, 1);
        PatientProgrammeDto dto = service.update(patient.getId(), pp.getId(),
                new UpdatePatientProgrammeRequest(null, newStart, null, null), adminCaller);

        assertThat(dto.startDate()).isEqualTo(newStart);
        assertThat(dto.endDate()).isEqualTo(newStart.plusWeeks(12));
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        PatientProgramme pp = assignment();
        when(patientProgrammeRepository.findById(pp.getId())).thenReturn(Optional.of(pp));

        assertThatThrownBy(() -> service.update(patient.getId(), pp.getId(),
                new UpdatePatientProgrammeRequest(null, null,
                        pp.getStartDate().minusDays(1), null), adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ---- fixtures ----------------------------------------------------------

    private CreatePatientProgrammeRequest request(CatalogueItemType type, UUID itemId,
                                                  LocalDate startDate, BigDecimal priceOverride) {
        return new CreatePatientProgrammeRequest(type,
                type == CatalogueItemType.PROGRAMME ? itemId : null,
                type == CatalogueItemType.SESSION ? itemId : null,
                type == CatalogueItemType.CHALLENGE ? itemId : null,
                startDate, priceOverride, null, null);
    }

    private PatientProgramme assignment() {
        PatientProgramme pp = withId(new PatientProgramme());
        pp.setPatient(patient);
        pp.setServiceType(CatalogueItemType.PROGRAMME);
        pp.setProgrammeId(UUID.randomUUID());
        pp.setStartDate(LocalDate.of(2026, 7, 20));
        pp.setEndDate(LocalDate.of(2026, 10, 12));
        pp.setPriceInr(new BigDecimal("4999.00"));
        pp.setAssignedBy(admin);
        return pp;
    }

    private Programme publishedProgramme(int weeks, String price) {
        Programme programme = withId(new Programme());
        fillItem(programme, price);
        programme.setDurationWeeks(weeks);
        return programme;
    }

    private Session publishedSession(int minutes, String price) {
        Session session = withId(new Session());
        fillItem(session, price);
        session.setDurationMinutes(minutes);
        return session;
    }

    private Challenge publishedChallenge(int days, String price) {
        Challenge challenge = withId(new Challenge());
        fillItem(challenge, price);
        challenge.setDurationDays(days);
        challenge.setGoalDescription("Goal");
        return challenge;
    }

    private void fillItem(com.poshanforlife.api.entity.CatalogueItem item, String price) {
        item.setName("Service");
        item.setServiceCode("SVC-001");
        item.setType("General");
        item.setPriceInr(new BigDecimal(price));
        item.setStatus(CatalogueStatus.PUBLISHED);
        item.setCreatedBy(admin);
    }

    private User newUser(String name, Role role) {
        User user = withId(new User());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@poshan.test");
        user.setRole(role);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> details(ApiException ex) {
        return (Map<String, String>) ex.getDetails();
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
