package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.ConvertLeadRequest;
import com.poshanforlife.api.dto.ConvertLeadResponseDto;
import com.poshanforlife.api.dto.CreateLeadActivityRequest;
import com.poshanforlife.api.dto.CreateLeadRequest;
import com.poshanforlife.api.dto.CreatePatientProgrammeRequest;
import com.poshanforlife.api.dto.CreatePatientRequest;
import com.poshanforlife.api.dto.LeadActivityDto;
import com.poshanforlife.api.dto.LeadDetailDto;
import com.poshanforlife.api.dto.LeadListItemDto;
import com.poshanforlife.api.dto.LeadSummaryDto;
import com.poshanforlife.api.dto.PatientDetailDto;
import com.poshanforlife.api.dto.ScheduleFollowupRequest;
import com.poshanforlife.api.dto.ServiceRefDto;
import com.poshanforlife.api.dto.UpdateLeadRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.LeadActivity;
import com.poshanforlife.api.entity.LeadActivityType;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.LeadActivityRepository;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.UserRepository;
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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM leads: the pipeline (NEW..CONVERTED/LOST), their activity timeline, and
 * the convert-to-patient flow. Reads/writes are ADMIN+DOCTOR; DOCTOR callers
 * are hard-scoped to leads assigned to them (assignedPractitioner), matching
 * the same access-scoping pattern as patients/reports.
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final UserRepository userRepository;
    private final ProgrammeRepository programmeRepository;
    private final NotificationService notificationService;
    private final PatientService patientService;
    private final PatientProgrammeService patientProgrammeService;

    @Transactional(readOnly = true)
    public LeadListResult list(String stage, String search, UUID practitionerId, String source,
                               boolean followupToday, int page, int limit, AuthenticatedUser caller) {
        LeadStage stageFilter = WireEnums.parse(stage, LeadStage::fromWire,
                "stage must be one of new, contacted, qualified, proposed, converted, lost");
        LeadSource sourceFilter = WireEnums.parse(source, LeadSource::fromWire,
                "source must be one of referral, social_media, website, walk_in, phone, whatsapp, mobile_app, other");
        String term = (search == null || search.isBlank()) ? "" : search.trim();

        // DOCTOR is hard-scoped to their own leads; practitionerId is ADMIN-only.
        UUID doctorId = caller.role() == Role.DOCTOR ? UUID.fromString(caller.id()) : practitionerId;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();

        var pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending());
        Page<Lead> result = leadRepository.search(stageFilter, doctorId, sourceFilter, followupToday,
                term, todayStart, todayEnd, pageable);
        LeadRepository.LeadStats stats = leadRepository.countStats(doctorId, sourceFilter, term,
                todayStart, todayEnd, weekStart,
                LeadStage.NEW, LeadStage.CONTACTED, LeadStage.QUALIFIED, LeadStage.PROPOSED,
                LeadStage.CONVERTED, LeadStage.LOST);

        return new LeadListResult(result.map(this::toListItem), toSummaryDto(stats));
    }

    @Transactional
    public LeadDetailDto create(CreateLeadRequest request, AuthenticatedUser caller) {
        UUID callerId = UUID.fromString(caller.id());

        User assignedPractitioner;
        if (caller.role() == Role.DOCTOR) {
            if (request.assignedPractitionerId() != null && !request.assignedPractitionerId().equals(callerId)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Doctors cannot assign leads to another doctor");
            }
            assignedPractitioner = userRepository.getReferenceById(callerId);
        } else {
            assignedPractitioner = findPractitioner(request.assignedPractitionerId());
        }

        Lead lead = new Lead();
        lead.setName(request.name().trim());
        lead.setPhone(request.phone());
        lead.setEmail(request.email());
        lead.setCity(request.city());
        lead.setAge(request.age());
        lead.setGender(request.gender());
        lead.setSource(request.source());
        lead.setHealthGoal(request.healthGoal());
        lead.setNotes(request.notes());
        lead.setStage(LeadStage.NEW);
        lead.setAssignedPractitioner(assignedPractitioner);
        lead.setInterestedProgramme(findProgramme(request.interestedProgrammeId()));
        lead.setNextFollowupAt(request.nextFollowupAt());
        lead.setCreatedBy(userRepository.getReferenceById(callerId));
        lead = leadRepository.save(lead);
        return toDetail(lead, List.of());
    }

    @Transactional(readOnly = true)
    public LeadDetailDto get(UUID id, AuthenticatedUser caller) {
        Lead lead = findLead(id);
        requireAccess(lead, caller);
        List<LeadActivity> activities = leadActivityRepository.findByLeadIdOrderByCreatedAtAsc(id);
        return toDetail(lead, activities);
    }

    /** When stage changes, a STAGE_CHANGE activity is auto-created in the same transaction. */
    @Transactional
    public LeadDetailDto update(UUID id, UpdateLeadRequest request, AuthenticatedUser caller) {
        Lead lead = findLead(id);
        requireAccess(lead, caller);
        UUID callerId = UUID.fromString(caller.id());

        if (request.name() != null) lead.setName(request.name().trim());
        if (request.phone() != null) lead.setPhone(request.phone());
        if (request.email() != null) lead.setEmail(request.email());
        if (request.city() != null) lead.setCity(request.city());
        if (request.age() != null) lead.setAge(request.age());
        if (request.gender() != null) lead.setGender(request.gender());
        if (request.healthGoal() != null) lead.setHealthGoal(request.healthGoal());
        if (request.notes() != null) lead.setNotes(request.notes());
        if (request.assignedPractitionerId() != null) {
            lead.setAssignedPractitioner(findPractitioner(request.assignedPractitionerId()));
        }
        if (request.interestedProgrammeId() != null) {
            lead.setInterestedProgramme(findProgramme(request.interestedProgrammeId()));
        }
        if (request.nextFollowupAt() != null) lead.setNextFollowupAt(request.nextFollowupAt());

        if (request.stage() != null && request.stage() != lead.getStage()) {
            LeadStage oldStage = lead.getStage();
            lead.setStage(request.stage());
            LeadActivity activity = new LeadActivity();
            activity.setLead(lead);
            activity.setActivityType(LeadActivityType.STAGE_CHANGE);
            activity.setDescription("Stage changed from " + oldStage.toWire() + " to " + request.stage().toWire());
            activity.setOldStage(oldStage);
            activity.setNewStage(request.stage());
            activity.setCreatedBy(userRepository.getReferenceById(callerId));
            leadActivityRepository.save(activity);
        }

        lead = leadRepository.save(lead);
        List<LeadActivity> activities = leadActivityRepository.findByLeadIdOrderByCreatedAtAsc(id);
        return toDetail(lead, activities);
    }

    @Transactional
    public LeadActivityDto addActivity(UUID id, CreateLeadActivityRequest request, AuthenticatedUser caller) {
        Lead lead = findLead(id);
        requireAccess(lead, caller);

        LeadActivity activity = new LeadActivity();
        activity.setLead(lead);
        activity.setActivityType(request.activityType());
        activity.setDescription(request.description());
        activity.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));
        return toActivityDto(leadActivityRepository.save(activity));
    }

    /**
     * Transactionally: create the User+PatientProfile (reusing PatientService),
     * optionally the Order+PatientProgramme (reusing PatientProgrammeService),
     * set the lead to CONVERTED, log a CONVERTED activity, and link the
     * lead to the new patient. All in one DB transaction: any failure (e.g.
     * email conflict, invalid catalogue item) rolls the whole thing back.
     */
    @Transactional
    public ConvertLeadResponseDto convert(UUID id, ConvertLeadRequest request, AuthenticatedUser caller) {
        Lead lead = findLead(id);
        requireAccess(lead, caller);
        if (lead.getStage() == LeadStage.CONVERTED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This lead has already been converted");
        }

        String name = request.name() != null ? request.name() : lead.getName();
        String phone = request.phone() != null ? request.phone() : lead.getPhone();
        String email = request.email() != null ? request.email() : lead.getEmail();
        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "email is required to convert this lead (it has none on file)");
        }

        UUID assignedDoctorId = lead.getAssignedPractitioner() != null
                ? lead.getAssignedPractitioner().getId() : null;

        CreatePatientRequest patientRequest = new CreatePatientRequest(
                name, email, null, phone, request.dateOfBirth(), lead.getGender(),
                request.bloodGroup(), request.heightCm(), null, null, assignedDoctorId);
        PatientDetailDto createdPatient = patientService.create(patientRequest, caller);
        UUID patientId = UUID.fromString(createdPatient.id());

        if (request.assignServiceId() != null) {
            if (request.serviceType() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "serviceType is required when assignServiceId is set");
            }
            CreatePatientProgrammeRequest assignmentRequest = new CreatePatientProgrammeRequest(
                    request.serviceType(),
                    request.serviceType() == CatalogueItemType.PROGRAMME ? request.assignServiceId() : null,
                    request.serviceType() == CatalogueItemType.SESSION ? request.assignServiceId() : null,
                    request.serviceType() == CatalogueItemType.CHALLENGE ? request.assignServiceId() : null,
                    request.startDate(), request.price(), null, assignedDoctorId);
            patientProgrammeService.create(patientId, assignmentRequest, caller);
        }

        lead.setStage(LeadStage.CONVERTED);
        lead.setConvertedPatient(userRepository.getReferenceById(patientId));
        leadRepository.save(lead);

        LeadActivity activity = new LeadActivity();
        activity.setLead(lead);
        activity.setActivityType(LeadActivityType.CONVERTED);
        activity.setDescription("Converted to patient " + name);
        activity.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));
        leadActivityRepository.save(activity);

        return new ConvertLeadResponseDto(patientId.toString(), "Lead converted to patient successfully");
    }

    /** Sets nextFollowupAt, logs a NOTE activity, and notifies the assigned practitioner (if any). */
    @Transactional
    public LeadDetailDto scheduleFollowup(UUID id, ScheduleFollowupRequest request, AuthenticatedUser caller) {
        Lead lead = findLead(id);
        requireAccess(lead, caller);

        Instant followupAt = request.followupAt().toInstant();
        lead.setNextFollowupAt(followupAt);
        lead = leadRepository.save(lead);

        String description = "Follow-up scheduled for " + request.followupAt().toLocalDate()
                + (request.message() != null && !request.message().isBlank() ? " — " + request.message() : "");
        LeadActivity activity = new LeadActivity();
        activity.setLead(lead);
        activity.setActivityType(LeadActivityType.NOTE);
        activity.setDescription(description);
        activity.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));
        leadActivityRepository.save(activity);

        if (lead.getAssignedPractitioner() != null) {
            String message = request.message() != null && !request.message().isBlank()
                    ? request.message()
                    : "Follow-up due for lead " + lead.getName() + " on " + request.followupAt().toLocalDate();
            notificationService.create(lead.getAssignedPractitioner(), Notification.TYPE_LEAD_FOLLOWUP,
                    "Follow-up reminder", message, "lead", lead.getId());
        }

        List<LeadActivity> activities = leadActivityRepository.findByLeadIdOrderByCreatedAtAsc(id);
        return toDetail(lead, activities);
    }

    // ---- helpers -----------------------------------------------------------

    private Lead findLead(UUID id) {
        return leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead", id));
    }

    private void requireAccess(Lead lead, AuthenticatedUser caller) {
        if (caller.role() == Role.DOCTOR
                && (lead.getAssignedPractitioner() == null
                    || !lead.getAssignedPractitioner().getId().equals(UUID.fromString(caller.id())))) {
            throw new AccessDeniedException("This lead is not assigned to you");
        }
    }

    private User findPractitioner(UUID id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "assignedPractitionerId does not refer to a DOCTOR user"));
    }

    private Programme findProgramme(UUID id) {
        if (id == null) {
            return null;
        }
        return programmeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "interestedProgrammeId does not refer to an existing programme"));
    }

    private LeadListItemDto toListItem(Lead l) {
        return new LeadListItemDto(l.getId().toString(), l.getName(), l.getPhone(), l.getEmail(), l.getCity(),
                l.getAge(), l.getGender(), l.getSource(), l.getStage(), userRef(l.getAssignedPractitioner()),
                programmeRef(l.getInterestedProgramme()), l.getNextFollowupAt(), l.getCreatedAt());
    }

    private LeadDetailDto toDetail(Lead l, List<LeadActivity> activities) {
        return new LeadDetailDto(l.getId().toString(), l.getName(), l.getPhone(), l.getEmail(), l.getCity(),
                l.getAge(), l.getGender(), l.getSource(), l.getHealthGoal(), l.getNotes(), l.getStage(),
                userRef(l.getAssignedPractitioner()), programmeRef(l.getInterestedProgramme()),
                l.getNextFollowupAt(),
                l.getConvertedPatient() != null ? l.getConvertedPatient().getId().toString() : null,
                userRef(l.getCreatedBy()), l.getCreatedAt(), l.getUpdatedAt(),
                activities.stream().map(this::toActivityDto).toList());
    }

    private LeadActivityDto toActivityDto(LeadActivity a) {
        return new LeadActivityDto(a.getId().toString(), a.getActivityType(), a.getDescription(),
                a.getOldStage(), a.getNewStage(), userRef(a.getCreatedBy()), a.getCreatedAt());
    }

    private static UserRefDto userRef(User u) {
        return u == null ? null : new UserRefDto(u.getId().toString(), u.getName());
    }

    private static ServiceRefDto programmeRef(Programme p) {
        return p == null ? null : ServiceRefDto.of(p);
    }

    private static LeadSummaryDto toSummaryDto(LeadRepository.LeadStats stats) {
        long total = nz(stats.getTotal());
        long converted = nz(stats.getConverted());
        Map<String, Long> stageCounts = Map.of(
                "new", nz(stats.getNewCount()),
                "contacted", nz(stats.getContacted()),
                "qualified", nz(stats.getQualified()),
                "proposed", nz(stats.getProposed()),
                "converted", converted,
                "lost", nz(stats.getLost()));
        BigDecimal conversionRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(converted).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return new LeadSummaryDto(stageCounts, nz(stats.getFollowupToday()), nz(stats.getNewThisWeek()),
                converted, conversionRate);
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    public record LeadListResult(Page<LeadListItemDto> page, LeadSummaryDto summary) {
    }
}
