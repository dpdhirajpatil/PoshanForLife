package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.OrderDetailDto;
import com.poshanforlife.api.dto.UpdateOrderRequest;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PaymentStatus;
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
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
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
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TransactionRepository transactionRepository;
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

    private OrderService orderService;

    private User admin;
    private User patient;
    private AuthenticatedUser adminCaller;

    @BeforeEach
    void setUp() {
        TransactionFactory transactionFactory =
                new TransactionFactory(transactionRepository, transactionNumbers, userRepository);
        orderService = new OrderService(orderRepository, transactionRepository,
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
    void markPaidCreatesActivationTransactionWhenNoneExists() {
        Order order = order("4999.00", PaymentStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(List.of());

        OrderDetailDto dto = orderService.update(order.getId(),
                new UpdateOrderRequest(PaymentStatus.PAID, null, null), adminCaller);

        assertThat(dto.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(tx.capture());
        assertThat(tx.getValue().getTransactionType()).isEqualTo(TransactionType.ACTIVATION);
        assertThat(tx.getValue().getInvoiceNumber()).isEqualTo("INV-202607-0042");
        assertThat(tx.getValue().getAmountInr()).isEqualByComparingTo("4999.00");
    }

    @Test
    void markPaidSkipsTransactionWhenOneAlreadyExists() {
        Order order = order("4999.00", PaymentStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(List.of(withId(new Transaction())));

        orderService.update(order.getId(),
                new UpdateOrderRequest(PaymentStatus.PAID, null, null), adminCaller);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void alreadyPaidOrderDoesNotCreateAnotherTransaction() {
        Order order = order("4999.00", PaymentStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.update(order.getId(),
                new UpdateOrderRequest(PaymentStatus.PAID, null, "settled offline"), adminCaller);

        verify(transactionRepository, never()).save(any());
        assertThat(order.getNotes()).isEqualTo("settled offline");
    }

    @Test
    void freeOrderMarkPaidCreatesNoTransaction() {
        Order order = order("0.00", PaymentStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.update(order.getId(),
                new UpdateOrderRequest(PaymentStatus.PAID, null, null), adminCaller);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void statusOnlyPatchDoesNotTouchLedger() {
        Order order = order("4999.00", PaymentStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderDetailDto dto = orderService.update(order.getId(),
                new UpdateOrderRequest(null, OrderStatus.COMPLETED, null), adminCaller);

        assertThat(dto.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(dto.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void doctorCannotTouchUnassignedPatientsOrder() {
        User doctor = newUser("Dr. Jones", Role.DOCTOR);
        AuthenticatedUser doctorCaller =
                new AuthenticatedUser(doctor.getId().toString(), "doc@poshan.test", Role.DOCTOR);
        Order order = order("4999.00", PaymentStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> orderService.get(order.getId(), doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listRejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> orderService.list("shipped", null, null, null, null,
                1, 10, adminCaller))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ---- fixtures ----------------------------------------------------------

    private Order order(String amount, PaymentStatus paymentStatus) {
        Order order = withId(new Order());
        order.setPatient(patient);
        order.setAmountInr(new BigDecimal(amount));
        order.setPaymentStatus(paymentStatus);
        order.setCreatedBy(admin);
        return order;
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
