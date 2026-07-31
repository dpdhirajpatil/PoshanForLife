package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.LeadStreakDto;
import com.poshanforlife.api.entity.LeadStreak;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.LeadStreakRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Self-only streak tracking for a LEAD user's own daily check-in (AN-22) —
 * mirrors {@link ChallengeProgressService}'s day-diff logic exactly (a
 * same-day check-in is a no-op, a one-day gap continues the streak, anything
 * wider resets to 1), reusing {@link BadgeEvaluationService#evaluateForStreak}
 * unmodified since it already takes a generic User.
 */
@Service
@RequiredArgsConstructor
public class LeadStreakService {

    private final LeadStreakRepository leadStreakRepository;
    private final UserRepository userRepository;
    private final BadgeEvaluationService badgeEvaluationService;

    @Transactional(readOnly = true)
    public LeadStreakDto get(AuthenticatedUser caller) {
        return toDto(getOrCreate(caller));
    }

    /** Idempotent per calendar day — repeat check-ins on the same day just return the current state. */
    @Transactional
    public LeadStreakDto checkIn(AuthenticatedUser caller) {
        LeadStreak streak = getOrCreate(caller);
        LocalDate today = LocalDate.now();
        if (!today.equals(streak.getLastLoggedDate())) {
            boolean continuesStreak = today.minusDays(1).equals(streak.getLastLoggedDate());
            streak.setCurrentStreak(continuesStreak ? streak.getCurrentStreak() + 1 : 1);
            streak.setLastLoggedDate(today);
            streak.setLongestStreak(Math.max(streak.getLongestStreak(), streak.getCurrentStreak()));
            streak = leadStreakRepository.save(streak);

            badgeEvaluationService.evaluateForStreak(streak.getLead(), streak.getLongestStreak());
        }
        return toDto(streak);
    }

    private LeadStreak getOrCreate(AuthenticatedUser caller) {
        UUID leadId = UUID.fromString(caller.id());
        return leadStreakRepository.findByLeadId(leadId).orElseGet(() -> {
            User lead = userRepository.findById(leadId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", leadId));
            LeadStreak streak = new LeadStreak();
            streak.setLead(lead);
            return leadStreakRepository.save(streak);
        });
    }

    private static LeadStreakDto toDto(LeadStreak s) {
        int percentComplete = Math.min(100,
                (int) Math.round(100.0 * s.getCurrentStreak() / LeadStreakDto.RING_TARGET_DAYS));
        return new LeadStreakDto(s.getCurrentStreak(), s.getLongestStreak(), s.getLastLoggedDate(), percentComplete);
    }
}
