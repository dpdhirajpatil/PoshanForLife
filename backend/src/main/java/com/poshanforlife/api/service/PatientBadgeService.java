package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.PatientBadgeStatusDto;
import com.poshanforlife.api.entity.Badge;
import com.poshanforlife.api.entity.PatientBadge;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.BadgeRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.PatientBadgeRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The full badge catalog annotated per-patient (earned + locked, so the
 * client can render unearned ones grayed-out) — mirrors
 * PatientProgrammeService's exact access-scoping rules (PATIENT self-only,
 * 404 not 403 on mismatch; DOCTOR scoped to their assigned patients).
 */
@Service
@RequiredArgsConstructor
public class PatientBadgeService {

    private final BadgeRepository badgeRepository;
    private final PatientBadgeRepository patientBadgeRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;

    @Transactional(readOnly = true)
    public List<PatientBadgeStatusDto> listForPatient(UUID patientId, AuthenticatedUser caller) {
        findPatient(patientId);
        requireAccess(patientId, caller);

        Map<UUID, PatientBadge> earnedByBadgeId = patientBadgeRepository.findByPatientId(patientId).stream()
                .collect(Collectors.toMap(pb -> pb.getBadge().getId(), pb -> pb));

        return badgeRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(b -> toDto(b, earnedByBadgeId.get(b.getId())))
                .toList();
    }

    private void findPatient(UUID id) {
        userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

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

    private static PatientBadgeStatusDto toDto(Badge badge, PatientBadge earned) {
        Instant earnedAt = earned == null ? null : earned.getEarnedAt();
        return new PatientBadgeStatusDto(badge.getId().toString(), badge.getName(), badge.getDescription(),
                badge.getIconKey(), badge.getCriteriaType(), badge.getCriteriaValue(), earned != null, earnedAt);
    }
}
