package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.PatientBadgeStatusDto;
import com.poshanforlife.api.entity.Badge;
import com.poshanforlife.api.entity.BadgeCriteriaType;
import com.poshanforlife.api.entity.PatientBadge;
import com.poshanforlife.api.repository.BadgeRepository;
import com.poshanforlife.api.repository.PatientBadgeRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A LEAD's own badge view (AN-22) — reuses the same badges/patient_badges
 * tables {@link PatientBadgeService} reads (patient_badges.patient_id is
 * just a users FK, not PATIENT-role-specific at the schema level), scoped to
 * the caller's own id and filtered to STREAK_DAYS criteria only:
 * challenge_completed/programme_count are permanently unearnable for a LEAD
 * (Leads have no PatientProgrammes by definition), so showing them would
 * just be permanently-locked clutter.
 */
@Service
@RequiredArgsConstructor
public class LeadBadgeService {

    private final BadgeRepository badgeRepository;
    private final PatientBadgeRepository patientBadgeRepository;

    @Transactional(readOnly = true)
    public List<PatientBadgeStatusDto> listForSelf(AuthenticatedUser caller) {
        UUID leadId = UUID.fromString(caller.id());
        Map<UUID, PatientBadge> earnedByBadgeId = patientBadgeRepository.findByPatientId(leadId).stream()
                .collect(Collectors.toMap(pb -> pb.getBadge().getId(), pb -> pb));

        return badgeRepository.findByCriteriaType(BadgeCriteriaType.STREAK_DAYS).stream()
                .map(b -> toDto(b, earnedByBadgeId.get(b.getId())))
                .toList();
    }

    private static PatientBadgeStatusDto toDto(Badge badge, PatientBadge earned) {
        Instant earnedAt = earned == null ? null : earned.getEarnedAt();
        return new PatientBadgeStatusDto(badge.getId().toString(), badge.getName(), badge.getDescription(),
                badge.getIconKey(), badge.getCriteriaType(), badge.getCriteriaValue(), earned != null, earnedAt);
    }
}
