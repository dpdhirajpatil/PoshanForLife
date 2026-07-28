package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.Badge;
import com.poshanforlife.api.entity.BadgeCriteriaType;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.PatientBadge;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PatientProgrammeStatus;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.repository.BadgeRepository;
import com.poshanforlife.api.repository.PatientBadgeRepository;
import com.poshanforlife.api.repository.PatientProgrammeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Server-triggered badge evaluation — never client-requested. Called from
 * {@link PatientProgrammeService#update} (on a status→COMPLETED transition)
 * and {@link ChallengeProgressService#checkIn} (after recomputing streaks).
 * custom-type badges have no automatic evaluation logic here; they exist for
 * a future manual-award or bespoke-criteria hook.
 */
@Service
@RequiredArgsConstructor
public class BadgeEvaluationService {

    private final BadgeRepository badgeRepository;
    private final PatientBadgeRepository patientBadgeRepository;
    private final PatientProgrammeRepository patientProgrammeRepository;
    private final NotificationService notificationService;

    /**
     * Called when a PatientProgramme's status just became COMPLETED.
     * challenge_completed fires on any completed challenge; programme_count
     * counts the patient's total completed PROGRAMME-type assignments
     * (sessions/challenges don't count toward it).
     */
    @Transactional
    public void evaluateForProgrammeCompletion(PatientProgramme pp) {
        User patient = pp.getPatient();

        if (pp.getServiceType() == CatalogueItemType.CHALLENGE) {
            award(patient, badgeRepository.findByCriteriaType(BadgeCriteriaType.CHALLENGE_COMPLETED));
        }

        if (pp.getServiceType() == CatalogueItemType.PROGRAMME) {
            long completedProgrammes = patientProgrammeRepository.countByPatientIdAndServiceTypeAndStatus(
                    patient.getId(), CatalogueItemType.PROGRAMME, PatientProgrammeStatus.COMPLETED);
            award(patient, badgeRepository.findByCriteriaType(BadgeCriteriaType.PROGRAMME_COUNT).stream()
                    .filter(b -> completedProgrammes >= b.getCriteriaValue())
                    .toList());
        }
    }

    /** Called after a check-in recomputes longestStreak — evaluated against the best-ever streak, not the current one (which can later reset). */
    @Transactional
    public void evaluateForStreak(User patient, int longestStreak) {
        award(patient, badgeRepository.findByCriteriaType(BadgeCriteriaType.STREAK_DAYS).stream()
                .filter(b -> longestStreak >= b.getCriteriaValue())
                .toList());
    }

    private void award(User patient, List<Badge> candidates) {
        for (Badge badge : candidates) {
            if (patientBadgeRepository.existsByPatientIdAndBadgeId(patient.getId(), badge.getId())) {
                continue;
            }
            PatientBadge earned = new PatientBadge();
            earned.setPatient(patient);
            earned.setBadge(badge);
            earned.setEarnedAt(Instant.now());
            patientBadgeRepository.save(earned);

            notificationService.create(patient, Notification.TYPE_BADGE_EARNED, "New badge earned!",
                    "You earned the \"" + badge.getName() + "\" badge", "badge", badge.getId());
        }
    }
}
