package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreatePatientProgrammeRequest;
import com.poshanforlife.api.dto.OrderSummaryDto;
import com.poshanforlife.api.dto.PatientProgrammeDto;
import com.poshanforlife.api.dto.ServiceRefDto;
import com.poshanforlife.api.dto.TransactionSummaryDto;
import com.poshanforlife.api.dto.UpdatePatientProgrammeRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.CatalogueStatus;
import com.poshanforlife.api.entity.Challenge;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PatientProgrammeStatus;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Role;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service assignments: the cross-entity flow that links a patient, a
 * published catalogue item, an order and (for priced items) an activation
 * transaction — all created atomically in one DB transaction. Reads (list,
 * get) are ADMIN+DOCTOR+PATIENT — DOCTOR scoped to their assigned patients,
 * PATIENT force-scoped to their own record only. Writes (create, update,
 * delete) remain ADMIN+DOCTOR only — a PATIENT never assigns/edits/removes
 * their own assignments (see PatientProgrammeController's method-level
 * annotations).
 */
@Service
@RequiredArgsConstructor
public class PatientProgrammeService {

    private final PatientProgrammeRepository patientProgrammeRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final ProgrammeRepository programmeRepository;
    private final SessionRepository sessionRepository;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final TransactionFactory transactionFactory;
    private final BadgeEvaluationService badgeEvaluationService;

    @Transactional(readOnly = true)
    public List<PatientProgrammeDto> list(UUID patientId, AuthenticatedUser caller) {
        findPatient(patientId);
        requireAccess(patientId, caller);

        List<PatientProgramme> assignments =
                patientProgrammeRepository.findByPatientIdOrderByCreatedAtDesc(patientId);

        // batch the commercial context: one query for orders, one for ledgers
        List<UUID> ppIds = assignments.stream().map(PatientProgramme::getId).toList();
        Map<UUID, Order> ordersByPp = ppIds.isEmpty() ? Map.of()
                : orderRepository.findByPatientProgrammeIdIn(ppIds).stream()
                        .collect(LinkedHashMap::new,
                                (m, o) -> m.put(o.getPatientProgramme().getId(), o), Map::putAll);
        List<UUID> orderIds = ordersByPp.values().stream().map(Order::getId).toList();
        Map<UUID, List<Transaction>> txByOrder = orderIds.isEmpty() ? Map.of()
                : groupByOrder(transactionRepository.findByOrderIdInOrderByCreatedAtDesc(orderIds));

        return assignments.stream()
                .map(pp -> toDto(pp,
                        ordersByPp.get(pp.getId()),
                        txByOrder.getOrDefault(orderOf(ordersByPp, pp), List.of())))
                .toList();
    }

    /**
     * The whole flow — validation, assignment row, order, activation
     * transaction — is one DB transaction: any failure rolls everything back.
     */
    @Transactional
    public PatientProgrammeDto create(UUID patientId, CreatePatientProgrammeRequest request,
                                      AuthenticatedUser caller) {
        User patient = findPatient(patientId);
        requireAccess(patientId, caller);

        CatalogueItemType type = request.serviceType();
        UUID itemId = resolveItemId(type, request);

        // (a) referenced catalogue item must exist and be published
        CatalogueItem item = findCatalogueItem(type, itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "The selected " + type.getLabel() + " does not exist"));
        if (item.getStatus() != CatalogueStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Only published catalogue items can be assigned ('" + item.getName()
                            + "' is " + item.getStatus().toWire() + ")");
        }

        UUID callerId = UUID.fromString(caller.id());
        User assignedDoctor = resolveAssignedDoctor(request.assignedDoctorId(), caller, callerId);

        // (b) endDate derived from the item's duration; sessions are a
        // single-day appointment (duration is minutes), so endDate = startDate
        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now();
        LocalDate endDate = deriveEndDate(item, startDate);

        BigDecimal price = request.priceInr() != null ? request.priceInr() : item.getPriceInr();

        // (c) the assignment itself
        PatientProgramme pp = new PatientProgramme();
        pp.setPatient(patient);
        pp.setServiceType(type);
        switch (type) {
            case PROGRAMME -> pp.setProgrammeId(itemId);
            case SESSION -> pp.setSessionId(itemId);
            case CHALLENGE -> pp.setChallengeId(itemId);
        }
        pp.setStartDate(startDate);
        pp.setEndDate(endDate);
        pp.setPriceInr(price);
        pp.setNotes(blankToNull(request.notes()));
        pp.setAssignedBy(userRepository.getReferenceById(callerId));
        pp.setAssignedDoctor(assignedDoctor);
        pp = patientProgrammeRepository.save(pp);

        // (d) the commercial record
        Order order = new Order();
        order.setPatient(patient);
        order.setPatientProgramme(pp);
        order.setAmountInr(price);
        order.setCreatedBy(userRepository.getReferenceById(callerId));
        order = orderRepository.save(order);

        // (e) priced items get an activation ledger entry immediately
        List<Transaction> transactions = price.signum() > 0
                ? List.of(transactionFactory.activation(order, callerId))
                : List.of();
        return toDto(pp, order, transactions);
    }

    @Transactional(readOnly = true)
    public PatientProgrammeDto get(UUID patientId, UUID ppId, AuthenticatedUser caller) {
        requireAccess(patientId, caller);
        PatientProgramme pp = findAssignment(patientId, ppId);
        return toDtoWithCommercials(pp);
    }

    /**
     * status/dates/notes only — the service reference is immutable (a
     * different service is a new assignment). When startDate moves and no
     * explicit endDate is given, endDate is re-derived from the catalogue
     * item's duration (left unchanged if that item was deleted meanwhile).
     */
    @Transactional
    public PatientProgrammeDto update(UUID patientId, UUID ppId,
                                      UpdatePatientProgrammeRequest request,
                                      AuthenticatedUser caller) {
        requireAccess(patientId, caller);
        PatientProgramme pp = findAssignment(patientId, ppId);

        boolean becomesCompleted = request.status() == PatientProgrammeStatus.COMPLETED
                && pp.getStatus() != PatientProgrammeStatus.COMPLETED;

        if (request.status() != null) pp.setStatus(request.status());
        if (request.notes() != null) pp.setNotes(blankToNull(request.notes()));
        if (request.startDate() != null) {
            pp.setStartDate(request.startDate());
            if (request.endDate() == null) {
                findCatalogueItem(pp.getServiceType(), pp.itemId())
                        .ifPresent(item -> pp.setEndDate(deriveEndDate(item, request.startDate())));
            }
        }
        if (request.endDate() != null) pp.setEndDate(request.endDate());
        if (pp.getEndDate() != null && pp.getEndDate().isBefore(pp.getStartDate())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "endDate cannot be before startDate");
        }

        // Badge evaluation (gamification feature) — same DB transaction as the
        // status change itself, so a newly-earned badge is never recorded
        // without the completion that triggered it (or vice versa).
        if (becomesCompleted) {
            badgeEvaluationService.evaluateForProgrammeCompletion(pp);
        }

        return toDtoWithCommercials(pp);
    }

    /**
     * Deleting is only for mistakes caught before money moved: blocked as
     * soon as any non-refund transaction exists for the linked order —
     * recorded payments must go through the refund flow instead. Otherwise
     * the assignment and its order are removed together.
     */
    @Transactional
    public void delete(UUID patientId, UUID ppId, AuthenticatedUser caller) {
        requireAccess(patientId, caller);
        PatientProgramme pp = findAssignment(patientId, ppId);

        Optional<Order> order = orderRepository.findByPatientProgrammeId(ppId);
        if (order.isPresent()) {
            if (transactionRepository.existsByOrderIdAndTransactionTypeNot(
                    order.get().getId(), TransactionType.REFUND)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "This assignment has recorded payment transactions and cannot be deleted. "
                                + "Record a refund via the Transactions flow instead.");
            }
            transactionRepository.deleteAll(
                    transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.get().getId()));
            orderRepository.delete(order.get());
        }
        patientProgrammeRepository.delete(pp);
    }

    // ---- helpers -----------------------------------------------------------

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private PatientProgramme findAssignment(UUID patientId, UUID ppId) {
        return patientProgrammeRepository.findById(ppId)
                .filter(pp -> pp.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", ppId));
    }

    /**
     * DOCTOR is scoped to their assigned patients (403 otherwise). PATIENT
     * is scoped to their own record only, and — unlike DOCTOR — gets a
     * plain 404 (not 403) on a mismatch: this must not confirm to the
     * caller whether a different patient's assignments exist, the same
     * reasoning as ReportService#get. In practice this branch only ever
     * runs for {@link #list} and {@link #get}: create/update/delete are
     * PATIENT-blocked at the controller before reaching this method.
     */
    private void requireAccess(UUID patientId, AuthenticatedUser caller) {
        if (caller.role() == Role.PATIENT && !patientId.toString().equals(caller.id())) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
        if (caller.role() == Role.DOCTOR
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                        UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }

    /** Exactly the id matching serviceType must be present; others must be absent. */
    private UUID resolveItemId(CatalogueItemType type, CreatePatientProgrammeRequest request) {
        Map<CatalogueItemType, UUID> provided = new LinkedHashMap<>();
        if (request.programmeId() != null) provided.put(CatalogueItemType.PROGRAMME, request.programmeId());
        if (request.sessionId() != null) provided.put(CatalogueItemType.SESSION, request.sessionId());
        if (request.challengeId() != null) provided.put(CatalogueItemType.CHALLENGE, request.challengeId());

        UUID itemId = provided.get(type);
        String field = type.getLabel() + "Id";
        if (itemId == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Validation failed",
                    Map.of(field, field + " is required when serviceType is " + type.getLabel()));
        }
        if (provided.size() > 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Only " + field + " may be set when serviceType is " + type.getLabel());
        }
        return itemId;
    }

    private Optional<? extends CatalogueItem> findCatalogueItemOf(CatalogueItemType type, UUID id) {
        return switch (type) {
            case PROGRAMME -> programmeRepository.findById(id);
            case SESSION -> sessionRepository.findById(id);
            case CHALLENGE -> challengeRepository.findById(id);
        };
    }

    private Optional<CatalogueItem> findCatalogueItem(CatalogueItemType type, UUID id) {
        return findCatalogueItemOf(type, id).map(CatalogueItem.class::cast);
    }

    private User resolveAssignedDoctor(UUID assignedDoctorId, AuthenticatedUser caller,
                                       UUID callerId) {
        if (caller.role() == Role.DOCTOR) {
            if (assignedDoctorId != null && !assignedDoctorId.equals(callerId)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Doctors cannot assign services on behalf of another doctor");
            }
            return userRepository.getReferenceById(callerId);
        }
        if (assignedDoctorId == null) {
            return null;
        }
        return userRepository.findById(assignedDoctorId)
                .filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "assignedDoctorId does not refer to a DOCTOR user"));
    }

    /**
     * Programmes run durationWeeks weeks, challenges durationDays days. A
     * session's duration is minutes — it is a single-day appointment, so its
     * endDate equals startDate.
     */
    private static LocalDate deriveEndDate(CatalogueItem item, LocalDate startDate) {
        return switch (item) {
            case Programme p -> startDate.plusWeeks(p.getDurationWeeks());
            case Challenge c -> startDate.plusDays(c.getDurationDays());
            default -> startDate;
        };
    }

    private PatientProgrammeDto toDtoWithCommercials(PatientProgramme pp) {
        Order order = orderRepository.findByPatientProgrammeId(pp.getId()).orElse(null);
        List<Transaction> transactions = order == null ? List.of()
                : transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        return toDto(pp, order, transactions);
    }

    private PatientProgrammeDto toDto(PatientProgramme pp, Order order,
                                      List<Transaction> transactions) {
        ServiceRefDto itemRef = findCatalogueItem(pp.getServiceType(), pp.itemId())
                .map(ServiceRefDto::of)
                .orElse(null);
        OrderSummaryDto orderDto = order == null ? null : new OrderSummaryDto(
                order.getId().toString(),
                order.getAmountInr(),
                order.getStatus(),
                order.getPaymentStatus(),
                transactions.stream().map(PatientProgrammeService::toSummary).toList());
        return new PatientProgrammeDto(
                pp.getId().toString(),
                pp.getServiceType(),
                itemRef,
                pp.getStartDate(),
                pp.getEndDate(),
                pp.getPriceInr(),
                pp.getStatus(),
                pp.getNotes(),
                userRef(pp.getAssignedBy()),
                userRef(pp.getAssignedDoctor()),
                orderDto,
                pp.getCreatedAt(),
                pp.getUpdatedAt());
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

    private static UUID orderOf(Map<UUID, Order> ordersByPp, PatientProgramme pp) {
        Order order = ordersByPp.get(pp.getId());
        return order == null ? null : order.getId();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Map<UUID, List<Transaction>> groupByOrder(List<Transaction> transactions) {
        Map<UUID, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction tx : transactions) {
            grouped.computeIfAbsent(tx.getOrder().getId(), k -> new ArrayList<>()).add(tx);
        }
        return grouped;
    }
}
