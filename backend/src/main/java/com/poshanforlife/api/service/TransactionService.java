package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateTransactionRequest;
import com.poshanforlife.api.dto.OrderProgrammeDto;
import com.poshanforlife.api.dto.PatientRefDto;
import com.poshanforlife.api.dto.ServiceRefDto;
import com.poshanforlife.api.dto.TransactionDetailDto;
import com.poshanforlife.api.dto.TransactionListItemDto;
import com.poshanforlife.api.dto.TransactionOrderDto;
import com.poshanforlife.api.dto.TransactionTotalsDto;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PaymentType;
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
import com.poshanforlife.api.util.WireEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The financial ledger. Reads are ADMIN+DOCTOR (DOCTOR scoped to their
 * patients); manual entry is ADMIN-only. Most transactions are created as a
 * side effect elsewhere (assignment activation, order mark-as-paid) via
 * {@link TransactionFactory} — this service's create() is for manual offline
 * entries and documents the same shape a future payment-gateway webhook
 * (source=mobile_app) would use.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Set<String> ALLOWED_SOURCES = Set.of("admin", "mobile_app");

    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final ProgrammeRepository programmeRepository;
    private final SessionRepository sessionRepository;
    private final ChallengeRepository challengeRepository;
    private final TransactionFactory transactionFactory;

    @Transactional(readOnly = true)
    public TransactionListResult list(String search, UUID userId, String catalogue,
                                      String paymentType, LocalDate dateFrom, LocalDate dateTo,
                                      int page, int limit, AuthenticatedUser caller) {
        PaymentType paymentTypeFilter = WireEnums.parse(paymentType, PaymentType::fromWire,
                "paymentType must be one of offline, online, credit");
        CatalogueItemType catalogueFilter = WireEnums.parse(catalogue, CatalogueItemType::fromWire,
                "catalogue must be one of programme, session, challenge");
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        Instant from = dateFrom != null
                ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.parse("9999-12-31T00:00:00Z");
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "dateTo cannot be before dateFrom");
        }

        UUID doctorId = caller.role() == Role.DOCTOR ? UUID.fromString(caller.id()) : null;
        // userId (practitioner) is an ADMIN-only filter; silently ignored for DOCTOR callers,
        // who are already hard-scoped to their own patients regardless
        UUID practitionerId = caller.role() == Role.ADMIN ? userId : null;

        Page<Transaction> txPage = transactionRepository.search(paymentTypeFilter, catalogueFilter,
                practitionerId, doctorId, term, from, to,
                PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending()));
        TransactionRepository.TransactionTotals totals = transactionRepository.sumTotals(
                paymentTypeFilter, catalogueFilter, practitionerId, doctorId, term, from, to);

        TransactionTotalsDto summary = new TransactionTotalsDto(
                totals.getTotalAmount(), totals.getTotalCredit().abs());
        return new TransactionListResult(txPage.map(this::toListItem), summary);
    }

    @Transactional(readOnly = true)
    public TransactionDetailDto get(UUID id, AuthenticatedUser caller) {
        return toDetail(findAccessible(id, caller));
    }

    /**
     * Manual entry (ADMIN) or a payment-gateway webhook tagging
     * source="mobile_app" + paymentGatewayRef — same validation either way.
     * priceInr is taken from the order's face amount; creditCharged defaults
     * to zero (no credit-wallet feature yet).
     */
    @Transactional
    public TransactionDetailDto create(CreateTransactionRequest request, AuthenticatedUser caller) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        PaymentType paymentType = request.paymentType() != null ? request.paymentType() : PaymentType.OFFLINE;
        BigDecimal discount = request.discountInr() != null ? request.discountInr() : BigDecimal.ZERO;
        String source = validateSource(request.source());

        Transaction tx = transactionFactory.record(order, request.transactionType(), paymentType,
                order.getAmountInr(), discount, request.amountInr(), BigDecimal.ZERO, source,
                request.paymentGatewayRef(), request.notes(), UUID.fromString(caller.id()));
        return toDetail(tx);
    }

    // ---- helpers -----------------------------------------------------------

    private String validateSource(String source) {
        if (source == null || source.isBlank()) {
            return "admin";
        }
        String normalized = source.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_SOURCES.contains(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "source must be one of admin, mobile_app");
        }
        return normalized;
    }

    private Transaction findAccessible(UUID id, AuthenticatedUser caller) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        if (caller.role() == Role.DOCTOR && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                UUID.fromString(caller.id()), tx.getPatient().getId())) {
            throw new AccessDeniedException("This patient's transactions are not assigned to you");
        }
        return tx;
    }

    private TransactionListItemDto toListItem(Transaction tx) {
        PatientProgramme pp = tx.getOrder().getPatientProgramme();
        CatalogueItem item = pp == null ? null
                : findCatalogueItem(pp.getServiceType(), pp.itemId()).orElse(null);
        return new TransactionListItemDto(
                tx.getId().toString(),
                tx.getTransactionId(),
                tx.getInvoiceNumber(),
                tx.getTransactionType(),
                tx.getPaymentType(),
                new UserRefDto(tx.getPatient().getId().toString(), tx.getPatient().getName()),
                pp != null ? pp.getServiceType() : null,
                item != null ? item.getName() : null,
                tx.getAmountInr(),
                tx.getCreditCharged(),
                userRef(tx.getCreatedBy()),
                tx.getCreatedAt());
    }

    private TransactionDetailDto toDetail(Transaction tx) {
        User patient = tx.getPatient();
        Order order = tx.getOrder();
        PatientProgramme pp = order.getPatientProgramme();
        OrderProgrammeDto ppDto = null;
        if (pp != null) {
            ServiceRefDto itemRef = findCatalogueItem(pp.getServiceType(), pp.itemId())
                    .map(ServiceRefDto::of).orElse(null);
            ppDto = new OrderProgrammeDto(
                    pp.getId().toString(),
                    pp.getServiceType(),
                    itemRef,
                    pp.getStartDate(),
                    pp.getEndDate(),
                    pp.getStatus(),
                    userRef(pp.getAssignedBy()),
                    userRef(pp.getAssignedDoctor()));
        }
        return new TransactionDetailDto(
                tx.getId().toString(),
                tx.getTransactionId(),
                tx.getInvoiceNumber(),
                tx.getTransactionType(),
                tx.getPaymentType(),
                tx.getPriceInr(),
                tx.getDiscountInr(),
                tx.getAmountInr(),
                tx.getCreditCharged(),
                tx.getSource(),
                tx.getPaymentGatewayRef(),
                tx.getNotes(),
                tx.getCreatedAt(),
                new PatientRefDto(patient.getId().toString(), patient.getName(),
                        patient.getEmail(), patient.getPhone()),
                userRef(tx.getCreatedBy()),
                new TransactionOrderDto(order.getId().toString(), ppDto));
    }

    private Optional<CatalogueItem> findCatalogueItem(CatalogueItemType type, UUID id) {
        return switch (type) {
            case PROGRAMME -> programmeRepository.findById(id).map(CatalogueItem.class::cast);
            case SESSION -> sessionRepository.findById(id).map(CatalogueItem.class::cast);
            case CHALLENGE -> challengeRepository.findById(id).map(CatalogueItem.class::cast);
        };
    }

    private static UserRefDto userRef(User user) {
        return user == null ? null : new UserRefDto(user.getId().toString(), user.getName());
    }

    public record TransactionListResult(Page<TransactionListItemDto> page, TransactionTotalsDto summary) {
    }
}
