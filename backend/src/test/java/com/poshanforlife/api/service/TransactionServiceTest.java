package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateTransactionRequest;
import com.poshanforlife.api.dto.TransactionDetailDto;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.TransactionType;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.OrderRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private ProgrammeRepository programmeRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionNumbers transactionNumbers;

    private TransactionService transactionService;

    private User admin;
    private User patient;
    private AuthenticatedUser adminCaller;

    @BeforeEach
    void setUp() {
        TransactionFactory transactionFactory =
                new TransactionFactory(transactionRepository, transactionNumbers, userRepository);
        transactionService = new TransactionService(transactionRepository, orderRepository,
                doctorPatientRepository, programmeRepository, sessionRepository,
                challengeRepository, transactionFactory);
        admin = newUser("Admin", Role.ADMIN);
        patient = newUser("Pat Kumar", Role.PATIENT);
        adminCaller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);

        lenient().when(userRepository.getReferenceById(admin.getId())).thenReturn(admin);
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(transactionNumbers.newTransactionId()).thenReturn("TRNID1749123456789AB2C3");
        lenient().when(transactionNumbers.nextInvoiceNumber()).thenReturn("INV-202607-0042");
    }

    @Test
    void manualEntrySavesAgainstOrderWithPriceFromOrder() {
        Order order = order("4999.00");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        TransactionDetailDto dto = transactionService.create(
                new CreateTransactionRequest(order.getId(), TransactionType.ACTIVATION,
                        new BigDecimal("4500.00"), new BigDecimal("499.00"), null, null, null, null),
                adminCaller);

        assertThat(dto.priceInr()).isEqualByComparingTo("4999.00");
        assertThat(dto.discountInr()).isEqualByComparingTo("499.00");
        assertThat(dto.amountInr()).isEqualByComparingTo("4500.00");
        assertThat(dto.paymentType()).isEqualTo(PaymentType.OFFLINE);
        assertThat(dto.source()).isEqualTo("admin");
        assertThat(dto.invoiceNumber()).isEqualTo("INV-202607-0042");
    }

    @Test
    void manualEntryAcceptsMobileAppSourceAndGatewayRef() {
        Order order = order("999.00");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        TransactionDetailDto dto = transactionService.create(
                new CreateTransactionRequest(order.getId(), TransactionType.ACTIVATION,
                        new BigDecimal("999.00"), null, PaymentType.ONLINE, "pay_Abc123",
                        "webhook", "mobile_app"),
                adminCaller);

        assertThat(dto.source()).isEqualTo("mobile_app");
        assertThat(dto.paymentGatewayRef()).isEqualTo("pay_Abc123");
        assertThat(dto.paymentType()).isEqualTo(PaymentType.ONLINE);
    }

    @Test
    void manualEntryRejectsUnknownSource() {
        Order order = order("999.00");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> transactionService.create(
                new CreateTransactionRequest(order.getId(), TransactionType.ACTIVATION,
                        new BigDecimal("999.00"), null, null, null, null, "webhook_bogus"),
                adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void manualEntryDefaultsCreditChargedToZero() {
        Order order = order("999.00");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        transactionService.create(
                new CreateTransactionRequest(order.getId(), TransactionType.REFUND,
                        new BigDecimal("999.00"), null, null, null, null, null),
                adminCaller);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCreditCharged()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTransactionType()).isEqualTo(TransactionType.REFUND);
    }

    @Test
    void listSummaryReflectsWholeFilteredSetNotJustCurrentPage() {
        Transaction tx = transaction("500.00", "0");
        Page<Transaction> onePageOfMany = new PageImpl<>(List.of(tx), PageRequest.of(0, 1), 5);
        when(transactionRepository.search(any(), any(), any(), any(), eq(""), any(), any(), any()))
                .thenReturn(onePageOfMany);
        TransactionRepository.TransactionTotals totals = mock(TransactionRepository.TransactionTotals.class);
        when(totals.getTotalAmount()).thenReturn(new BigDecimal("12500.00"));
        when(totals.getTotalCredit()).thenReturn(new BigDecimal("-300.00"));
        when(transactionRepository.sumTotals(any(), any(), any(), any(), eq(""), any(), any()))
                .thenReturn(totals);

        TransactionService.TransactionListResult result = transactionService.list(
                null, null, null, null, null, null, 1, 1, adminCaller);

        assertThat(result.page().getTotalElements()).isEqualTo(5);
        assertThat(result.page().getContent()).hasSize(1);
        assertThat(result.summary().totalTransactionValue()).isEqualByComparingTo("12500.00");
        // credit consumed reported as a positive figure (creditCharged is negative-or-zero)
        assertThat(result.summary().totalCreditConsumed()).isEqualByComparingTo("300.00");
    }

    @Test
    void doctorPractitionerFilterIsIgnored() {
        User doctor = newUser("Dr. Jones", Role.DOCTOR);
        AuthenticatedUser doctorCaller =
                new AuthenticatedUser(doctor.getId().toString(), "doc@poshan.test", Role.DOCTOR);
        Page<Transaction> empty = new PageImpl<>(List.of());
        when(transactionRepository.search(any(), any(), isNull(), eq(doctor.getId()),
                eq(""), any(), any(), any())).thenReturn(empty);
        TransactionRepository.TransactionTotals totals = mock(TransactionRepository.TransactionTotals.class);
        when(totals.getTotalAmount()).thenReturn(BigDecimal.ZERO);
        when(totals.getTotalCredit()).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumTotals(any(), any(), isNull(), eq(doctor.getId()),
                eq(""), any(), any())).thenReturn(totals);

        // an ADMIN-only userId filter passed by a DOCTOR caller must not narrow their own scope
        transactionService.list(null, admin.getId(), null, null, null, null, 1, 10, doctorCaller);

        verify(transactionRepository).search(any(), any(), isNull(), eq(doctor.getId()),
                eq(""), any(), any(), any());
    }

    @Test
    void listRejectsUnknownPaymentTypeFilter() {
        assertThatThrownBy(() -> transactionService.list(null, null, null, "bogus",
                null, null, 1, 10, adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void listRejectsUnknownCatalogueFilter() {
        assertThatThrownBy(() -> transactionService.list(null, null, "bogus", null,
                null, null, 1, 10, adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void doctorCannotAccessUnassignedPatientsTransaction() {
        User doctor = newUser("Dr. Jones", Role.DOCTOR);
        AuthenticatedUser doctorCaller =
                new AuthenticatedUser(doctor.getId().toString(), "doc@poshan.test", Role.DOCTOR);
        Transaction tx = transaction("500.00", "0");
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> transactionService.get(tx.getId(), doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void detailIncludesNestedAssignmentAndCatalogueItem() {
        Programme programme = withId(new Programme());
        programme.setName("Fat Loss 12-Week");
        programme.setServiceCode("PRG-001");
        programme.setDurationWeeks(12);
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

        PatientProgramme pp = withId(new PatientProgramme());
        pp.setServiceType(CatalogueItemType.PROGRAMME);
        pp.setProgrammeId(programme.getId());
        pp.setStartDate(java.time.LocalDate.of(2026, 7, 20));
        pp.setEndDate(java.time.LocalDate.of(2026, 10, 12));
        pp.setStatus(com.poshanforlife.api.entity.PatientProgrammeStatus.ACTIVE);

        Order order = order("4999.00");
        order.setPatientProgramme(pp);
        Transaction tx = transaction("4999.00", "0");
        tx.setOrder(order);
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        TransactionDetailDto dto = transactionService.get(tx.getId(), adminCaller);

        assertThat(dto.order().patientProgramme().catalogueItem().name()).isEqualTo("Fat Loss 12-Week");
        assertThat(dto.order().patientProgramme().catalogueItem().durationWeeks()).isEqualTo(12);
        assertThat(dto.order().patientProgramme().serviceType()).isEqualTo(CatalogueItemType.PROGRAMME);
        assertThat(dto.patient().name()).isEqualTo("Pat Kumar");
    }

    // ---- fixtures ----------------------------------------------------------

    private Order order(String amount) {
        Order order = withId(new Order());
        order.setPatient(patient);
        order.setAmountInr(new BigDecimal(amount));
        order.setCreatedBy(admin);
        return order;
    }

    private Transaction transaction(String amount, String creditCharged) {
        Transaction tx = withId(new Transaction());
        Order order = order(amount);
        tx.setOrder(order);
        tx.setPatient(patient);
        tx.setCreatedBy(admin);
        tx.setTransactionId("TRNID1749123456789AB2C3");
        tx.setInvoiceNumber("INV-202607-0001");
        tx.setTransactionType(TransactionType.ACTIVATION);
        tx.setPaymentType(PaymentType.OFFLINE);
        tx.setPriceInr(new BigDecimal(amount));
        tx.setAmountInr(new BigDecimal(amount));
        tx.setCreditCharged(new BigDecimal(creditCharged));
        return tx;
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
