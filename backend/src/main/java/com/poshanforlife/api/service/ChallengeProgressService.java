package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.ChallengeProgressDto;
import com.poshanforlife.api.dto.CheckInRequest;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Challenge;
import com.poshanforlife.api.entity.ChallengeProgress;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.ChallengeProgressRepository;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.PatientProgrammeRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily check-in tracking for a challenge-type PatientProgramme. Reads are
 * ADMIN+DOCTOR+PATIENT (same scoping as PatientProgrammeService); the
 * check-in write is PATIENT-self-only (see ChallengeProgressController's
 * @PatientOnly on PATCH) — currentStreak/longestStreak/percentComplete are
 * always server-computed from lastLoggedDate, never trusted from the client.
 */
@Service
@RequiredArgsConstructor
public class ChallengeProgressService {

    private final ChallengeProgressRepository challengeProgressRepository;
    private final PatientProgrammeRepository patientProgrammeRepository;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final BadgeEvaluationService badgeEvaluationService;

    @Transactional(readOnly = true)
    public ChallengeProgressDto get(UUID patientId, UUID ppId, AuthenticatedUser caller) {
        requireAccess(patientId, caller);
        PatientProgramme pp = findChallengeAssignment(patientId, ppId);
        return toDto(getOrCreate(pp));
    }

    /**
     * Only checkedInToday=true is supported. A repeat check-in on the same
     * calendar day is a no-op (idempotent); a gap of more than one day resets
     * currentStreak to 1 rather than continuing it.
     */
    @Transactional
    public ChallengeProgressDto checkIn(UUID patientId, UUID ppId, CheckInRequest request,
                                        AuthenticatedUser caller) {
        // @PatientOnly already restricts the role; still enforce self-only —
        // 404 (not 403) on a mismatch, same reasoning as PatientProgrammeService.
        if (!patientId.toString().equals(caller.id())) {
            throw new ResourceNotFoundException("Assignment", ppId);
        }
        if (!Boolean.TRUE.equals(request.checkedInToday())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "checkedInToday must be true");
        }

        PatientProgramme pp = findChallengeAssignment(patientId, ppId);
        ChallengeProgress progress = getOrCreate(pp);

        LocalDate today = LocalDate.now();
        if (!today.equals(progress.getLastLoggedDate())) {
            boolean continuesStreak = today.minusDays(1).equals(progress.getLastLoggedDate());
            progress.setCurrentStreak(continuesStreak ? progress.getCurrentStreak() + 1 : 1);
            progress.setLastLoggedDate(today);
            progress.setLongestStreak(Math.max(progress.getLongestStreak(), progress.getCurrentStreak()));
            progress.setPercentComplete(computePercentComplete(pp, progress.getCurrentStreak()));
            progress = challengeProgressRepository.save(progress);

            badgeEvaluationService.evaluateForStreak(pp.getPatient(), progress.getLongestStreak());
        }
        return toDto(progress);
    }

    // ---- helpers -----------------------------------------------------------

    private ChallengeProgress getOrCreate(PatientProgramme pp) {
        return challengeProgressRepository.findByPatientProgrammeId(pp.getId())
                .orElseGet(() -> {
                    ChallengeProgress progress = new ChallengeProgress();
                    progress.setPatientProgramme(pp);
                    return challengeProgressRepository.save(progress);
                });
    }

    private PatientProgramme findChallengeAssignment(UUID patientId, UUID ppId) {
        PatientProgramme pp = patientProgrammeRepository.findById(ppId)
                .filter(p -> p.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", ppId));
        if (pp.getServiceType() != CatalogueItemType.CHALLENGE) {
            throw new ResourceNotFoundException("Assignment", ppId);
        }
        return pp;
    }

    /** Falls back to 0 if the underlying Challenge catalogue item was since archived/deleted. */
    private int computePercentComplete(PatientProgramme pp, int currentStreak) {
        Integer totalDays = challengeRepository.findById(pp.itemId()).map(Challenge::getDurationDays).orElse(null);
        if (totalDays == null || totalDays <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(100.0 * currentStreak / totalDays));
    }

    private void requireAccess(UUID patientId, AuthenticatedUser caller) {
        findPatient(patientId);
        if (caller.role() == Role.PATIENT && !patientId.toString().equals(caller.id())) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
        if (caller.role() == Role.DOCTOR
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                        UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }

    private void findPatient(UUID id) {
        userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private static ChallengeProgressDto toDto(ChallengeProgress p) {
        return new ChallengeProgressDto(p.getId().toString(), p.getPatientProgramme().getId().toString(),
                p.getCurrentStreak(), p.getLongestStreak(), p.getLastLoggedDate(),
                p.getPercentComplete(), p.getUpdatedAt());
    }
}
