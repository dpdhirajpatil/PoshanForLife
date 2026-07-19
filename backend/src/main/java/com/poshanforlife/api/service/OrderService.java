package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.OrderDetailDto;
import com.poshanforlife.api.dto.OrderListItemDto;
import com.poshanforlife.api.dto.OrderProgrammeDto;
import com.poshanforlife.api.dto.PatientRefDto;
import com.poshanforlife.api.dto.TransactionSummaryDto;
import com.poshanforlife.api.dto.UpdateOrderRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PaymentStatus;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.OrderRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.SessionRepository;
import com.poshanforlife.api.repository.TransactionRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Orders — the commercial records created by the service-assignment flow.
 * ADMIN sees all; DOCTOR only their assigned patients' orders. There is
 * deliberately no create() here: orders exist only as a side effect of
 * assigning a service (matching the original API).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final ProgrammeRepository programmeRepository;
    private final SessionRepository sessionRepository;
    private final ChallengeRepository challengeRepository;
    private final TransactionFactory transactionFactory;

    @Transactional(readOnly = true)
    public Page<OrderListItemDto> list(String status, String paymentStatus, String search,
                                       LocalDate dateFrom, LocalDate dateTo,
                                       int page, int limit, AuthenticatedUser caller) {
        OrderStatus statusFilter = parseEnum(status, OrderStatus::fromWire,
                "status must be one of active, completed, deactivated");
        PaymentStatus paymentFilter = parseEnum(paymentStatus, PaymentStatus::fromWire,
                "paymentStatus must be one of paid, unpaid, pending");
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        // open-ended range instead of null timestamp params
        Instant from = dateFrom != null
                ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.parse("9999-12-31T00:00:00Z");
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "dateTo cannot be before dateFrom");
        }

        UUID doctorId = caller.role() == Role.DOCTOR ? UUID.fromString(caller.id()) : null;
        Page<Order> orders = orderRepository.search(statusFilter, paymentFilter,
                doctorId, term, from, to,
                PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending()));
        return orders.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto get(UUID id, AuthenticatedUser caller) {
        Order order = findAccessible(id, caller);
        return toDetail(order);
    }

    /**
     * paymentStatus/status/notes only. Transitioning paymentStatus to PAID
     * auto-creates an activation transaction in this same DB transaction —
     * but only when the order has no transaction yet (the assignment flow
     * already created one for priced items) and the amount is > 0 (a free
     * order has nothing to invoice, matching prompt 06's rule).
     */
    @Transactional
    public OrderDetailDto update(UUID id, UpdateOrderRequest request,
                                 AuthenticatedUser caller) {
        Order order = findAccessible(id, caller);

        boolean becomesPaid = request.paymentStatus() == PaymentStatus.PAID
                && order.getPaymentStatus() != PaymentStatus.PAID;
        if (request.paymentStatus() != null) order.setPaymentStatus(request.paymentStatus());
        if (request.status() != null) order.setStatus(request.status());
        if (request.notes() != null) {
            order.setNotes(request.notes().isBlank() ? null : request.notes().trim());
        }

        if (becomesPaid
                && order.getAmountInr().signum() > 0
                && transactionRepository.findByOrderIdOrderByCreatedAtDesc(id).isEmpty()) {
            transactionFactory.activation(order, UUID.fromString(caller.id()));
        }
        return toDetail(order);
    }

    // ---- helpers -----------------------------------------------------------

    private Order findAccessible(UUID id, AuthenticatedUser caller) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (caller.role() == Role.DOCTOR && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                UUID.fromString(caller.id()), order.getPatient().getId())) {
            throw new AccessDeniedException("This patient's orders are not assigned to you");
        }
        return order;
    }

    private OrderListItemDto toListItem(Order order) {
        PatientProgramme pp = order.getPatientProgramme();
        CatalogueItem item = pp == null ? null
                : findCatalogueItem(pp.getServiceType(), pp.itemId()).orElse(null);
        return new OrderListItemDto(
                order.getId().toString(),
                new UserRefDto(order.getPatient().getId().toString(), order.getPatient().getName()),
                pp != null ? pp.getServiceType() : null,
                item != null ? item.getName() : null,
                order.getAmountInr(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getCreatedAt());
    }

    private OrderDetailDto toDetail(Order order) {
        User patient = order.getPatient();
        PatientProgramme pp = order.getPatientProgramme();
        OrderProgrammeDto ppDto = null;
        if (pp != null) {
            ppDto = new OrderProgrammeDto(
                    pp.getId().toString(),
                    pp.getServiceType(),
                    findCatalogueItem(pp.getServiceType(), pp.itemId())
                            .map(PatientProgrammeService::serviceRef).orElse(null),
                    pp.getStartDate(),
                    pp.getEndDate(),
                    pp.getStatus(),
                    userRef(pp.getAssignedBy()),
                    userRef(pp.getAssignedDoctor()));
        }
        List<TransactionSummaryDto> transactions = transactionRepository
                .findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .map(OrderService::toSummary)
                .toList();
        return new OrderDetailDto(
                order.getId().toString(),
                order.getAmountInr(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getNotes(),
                new PatientRefDto(patient.getId().toString(), patient.getName(),
                        patient.getEmail(), patient.getPhone()),
                ppDto,
                transactions,
                userRef(order.getCreatedBy()),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private Optional<CatalogueItem> findCatalogueItem(CatalogueItemType type, UUID id) {
        return switch (type) {
            case PROGRAMME -> programmeRepository.findById(id).map(CatalogueItem.class::cast);
            case SESSION -> sessionRepository.findById(id).map(CatalogueItem.class::cast);
            case CHALLENGE -> challengeRepository.findById(id).map(CatalogueItem.class::cast);
        };
    }

    private static TransactionSummaryDto toSummary(Transaction tx) {
        return new TransactionSummaryDto(
                tx.getId().toString(),
                tx.getTransactionId(),
                tx.getInvoiceNumber(),
                tx.getTransactionType(),
                tx.getPaymentType(),
                tx.getAmountInr(),
                tx.getCreatedAt());
    }

    private static UserRefDto userRef(User user) {
        return user == null ? null : new UserRefDto(user.getId().toString(), user.getName());
    }

    private static <E> E parseEnum(String value, java.util.function.Function<String, E> parser,
                                   String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
