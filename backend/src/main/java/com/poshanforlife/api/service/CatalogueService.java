package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CatalogueItemDto;
import com.poshanforlife.api.dto.CreateCatalogueItemRequest;
import com.poshanforlife.api.dto.UpdateCatalogueItemRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.CatalogueServiceCode;
import com.poshanforlife.api.entity.CatalogueStatus;
import com.poshanforlife.api.entity.Challenge;
import com.poshanforlife.api.entity.PatientProgrammeStatus;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Session;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.CatalogueItemRepository;
import com.poshanforlife.api.repository.CatalogueServiceCodeRepository;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.PatientProgrammeRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.SessionRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One service for all three catalogue resource groups — shared-field handling,
 * cross-table serviceCode uniqueness (via the catalogue_service_codes
 * registry) and the delete guard are written once and dispatched on
 * {@link CatalogueItemType}.
 */
@Service
@RequiredArgsConstructor
public class CatalogueService {

    private final ProgrammeRepository programmeRepository;
    private final SessionRepository sessionRepository;
    private final ChallengeRepository challengeRepository;
    private final CatalogueServiceCodeRepository serviceCodeRepository;
    private final PatientProgrammeRepository patientProgrammeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<CatalogueItemDto> list(CatalogueItemType type, String status, String search,
                                       int page, int limit) {
        CatalogueStatus statusFilter = parseStatus(status);
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        Page<CatalogueItem> items = repo(type).search(statusFilter, term,
                PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending()));

        Map<UUID, Long> activeCounts = activeCounts(type, items.getContent());
        return items.map(item -> toDto(item, activeCounts.getOrDefault(item.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public CatalogueItemDto get(CatalogueItemType type, UUID id) {
        CatalogueItem item = find(type, id);
        return toDto(item, countActive(type, id));
    }

    @Transactional
    public CatalogueItemDto create(CatalogueItemType type, CreateCatalogueItemRequest request,
                                   AuthenticatedUser caller) {
        requireTypeFields(type, request.durationWeeks(), request.durationMinutes(),
                request.durationDays(), request.goalDescription());

        String code = request.serviceCode().trim();
        ensureCodeAvailable(code, null);

        CatalogueItem item = newItem(type);
        item.setName(request.name().trim());
        item.setServiceCode(code);
        item.setType(request.type().trim());
        item.setPriceInr(request.priceInr());
        item.setDescription(blankToNull(request.description()));
        item.setCoverImageUrl(blankToNull(request.coverImageUrl()));
        item.setStatus(request.status() != null ? request.status() : CatalogueStatus.DRAFT);
        item.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));
        applyTypeFields(item, request.durationWeeks(), request.durationMinutes(),
                request.durationDays(), request.goalDescription());

        item = repo(type).save(item);
        registerCode(code, type, item.getId());
        return toDto(item, 0L);
    }

    @Transactional
    public CatalogueItemDto update(CatalogueItemType type, UUID id, UpdateCatalogueItemRequest request) {
        CatalogueItem item = find(type, id);

        if (request.serviceCode() != null) {
            String code = request.serviceCode().trim();
            ensureCodeAvailable(code, id);
            CatalogueServiceCode registered = serviceCodeRepository
                    .findByItemTypeAndItemId(type, id)
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing service-code registry row for " + type + " " + id));
            registered.setCode(code);
            item.setServiceCode(code);
        }
        if (request.name() != null) item.setName(request.name().trim());
        if (request.type() != null) item.setType(request.type().trim());
        if (request.priceInr() != null) item.setPriceInr(request.priceInr());
        if (request.description() != null) item.setDescription(blankToNull(request.description()));
        if (request.coverImageUrl() != null) item.setCoverImageUrl(blankToNull(request.coverImageUrl()));
        if (request.status() != null) item.setStatus(request.status());
        applyTypeFields(item, request.durationWeeks(), request.durationMinutes(),
                request.durationDays(), request.goalDescription());

        return toDto(item, countActive(type, id));
    }

    /**
     * Hard delete — allowed only when the item has no ACTIVE patient
     * assignments, or is archived (an archived item is already hidden from
     * sale, so cleanup is permitted).
     */
    @Transactional
    public void delete(CatalogueItemType type, UUID id) {
        CatalogueItem item = find(type, id);
        long active = countActive(type, id);
        if (item.getStatus() != CatalogueStatus.ARCHIVED && active > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot delete this " + type.getLabel() + ": " + active
                            + " active patient assignment(s) reference it. Archive it instead.");
        }
        serviceCodeRepository.findByItemTypeAndItemId(type, id)
                .ifPresent(serviceCodeRepository::delete);
        repo(type).delete(item);
    }

    // ---- helpers -----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private CatalogueItemRepository<CatalogueItem> repo(CatalogueItemType type) {
        return (CatalogueItemRepository<CatalogueItem>) (CatalogueItemRepository<?>) switch (type) {
            case PROGRAMME -> programmeRepository;
            case SESSION -> sessionRepository;
            case CHALLENGE -> challengeRepository;
        };
    }

    private CatalogueItem newItem(CatalogueItemType type) {
        return switch (type) {
            case PROGRAMME -> new Programme();
            case SESSION -> new Session();
            case CHALLENGE -> new Challenge();
        };
    }

    private CatalogueItem find(CatalogueItemType type, UUID id) {
        return repo(type).findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(capitalize(type.getLabel()), id));
    }

    /** The one type-specific rule: the matching duration (and goal) is required on create. */
    private void requireTypeFields(CatalogueItemType type, Integer durationWeeks,
                                   Integer durationMinutes, Integer durationDays,
                                   String goalDescription) {
        Map<String, String> errors = new LinkedHashMap<>();
        switch (type) {
            case PROGRAMME -> {
                if (durationWeeks == null) errors.put("durationWeeks", "durationWeeks is required for programmes");
            }
            case SESSION -> {
                if (durationMinutes == null) errors.put("durationMinutes", "durationMinutes is required for sessions");
            }
            case CHALLENGE -> {
                if (durationDays == null) errors.put("durationDays", "durationDays is required for challenges");
                if (goalDescription == null || goalDescription.isBlank()) {
                    errors.put("goalDescription", "goalDescription is required for challenges");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Validation failed", errors);
        }
    }

    /** Sets whichever type-specific fields are present; irrelevant ones are ignored. */
    private void applyTypeFields(CatalogueItem item, Integer durationWeeks, Integer durationMinutes,
                                 Integer durationDays, String goalDescription) {
        switch (item) {
            case Programme p -> {
                if (durationWeeks != null) p.setDurationWeeks(durationWeeks);
            }
            case Session s -> {
                if (durationMinutes != null) s.setDurationMinutes(durationMinutes);
            }
            case Challenge c -> {
                if (durationDays != null) c.setDurationDays(durationDays);
                if (goalDescription != null && !goalDescription.isBlank()) {
                    c.setGoalDescription(goalDescription.trim());
                }
            }
            default -> throw new IllegalStateException("Unknown catalogue item " + item.getClass());
        }
    }

    /** Global (cross-table) serviceCode uniqueness, case-insensitive. */
    private void ensureCodeAvailable(String code, UUID selfItemId) {
        serviceCodeRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getItemId().equals(selfItemId))
                .ifPresent(existing -> {
                    throw codeConflict(code);
                });
    }

    private void registerCode(String code, CatalogueItemType type, UUID itemId) {
        CatalogueServiceCode entry = new CatalogueServiceCode();
        entry.setCode(code);
        entry.setItemType(type);
        entry.setItemId(itemId);
        try {
            // flush now so a concurrent create loses here, not at commit
            serviceCodeRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw codeConflict(code);
        }
    }

    private ApiException codeConflict(String code) {
        return new ApiException(ErrorCode.VALIDATION_ERROR,
                "Service code '" + code + "' is already used by another catalogue item",
                Map.of("serviceCode", "This service code is already in use"));
    }

    private CatalogueStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CatalogueStatus.fromWire(status);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "status must be one of draft, published, archived");
        }
    }

    private long countActive(CatalogueItemType type, UUID itemId) {
        return patientProgrammeRepository.countForItem(type, itemId, PatientProgrammeStatus.ACTIVE);
    }

    private Map<UUID, Long> activeCounts(CatalogueItemType type, List<CatalogueItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = items.stream().map(CatalogueItem::getId).toList();
        return patientProgrammeRepository.countByItems(type, ids, PatientProgrammeStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        PatientProgrammeRepository.ItemAssignmentCount::getItemId,
                        PatientProgrammeRepository.ItemAssignmentCount::getTotal,
                        (a, b) -> a));
    }

    private CatalogueItemDto toDto(CatalogueItem item, long activeAssignments) {
        Integer durationWeeks = item instanceof Programme p ? p.getDurationWeeks() : null;
        Integer durationMinutes = item instanceof Session s ? s.getDurationMinutes() : null;
        Integer durationDays = item instanceof Challenge c ? c.getDurationDays() : null;
        String goalDescription = item instanceof Challenge c ? c.getGoalDescription() : null;
        return new CatalogueItemDto(
                item.getId().toString(),
                item.getName(),
                item.getServiceCode(),
                item.getType(),
                item.getPriceInr(),
                item.getDescription(),
                item.getCoverImageUrl(),
                item.getStatus(),
                durationWeeks,
                durationMinutes,
                durationDays,
                goalDescription,
                activeAssignments,
                new UserRefDto(item.getCreatedBy().getId().toString(), item.getCreatedBy().getName()),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
