package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Lead-specific streak tracking (AN-22) — a LEAD user has no PatientProgramme
 * to hang a {@link ChallengeProgress} row off, so this is a standalone
 * per-lead-user streak instead. Same lastLoggedDate+currentStreak-only shape,
 * evaluated against the same badges.STREAK_DAYS criteria via
 * BadgeEvaluationService.evaluateForStreak (reused unmodified — it takes a
 * generic User, not a PatientProgramme).
 */
@Getter
@Setter
@Entity
@Table(name = "lead_streaks")
public class LeadStreak extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_user_id", nullable = false, unique = true)
    private User lead;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "last_logged_date")
    private LocalDate lastLoggedDate;
}
