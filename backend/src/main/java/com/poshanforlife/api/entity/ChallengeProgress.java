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
 * Daily check-in tracking for one challenge-type PatientProgramme (exactly
 * one row per assignment — enforced by the DB unique constraint on
 * patient_programme_id and checked in the service layer that it's a
 * challenge, not a programme/session). No separate check-in-history table:
 * lastLoggedDate + currentStreak together are enough to derive whether
 * today's check-in continues, resets, or repeats a streak.
 */
@Getter
@Setter
@Entity
@Table(name = "challenge_progress")
public class ChallengeProgress extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_programme_id", nullable = false, unique = true)
    private PatientProgramme patientProgramme;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "last_logged_date")
    private LocalDate lastLoggedDate;

    /**
     * 0-100, derived from currentStreak against the challenge's total
     * durationDays — the simpler of the two computations prompt 06's model
     * supports (vs. elapsed-calendar-time), and consistent with streak_days
     * badges also keying off streak length.
     */
    @Column(name = "percent_complete", nullable = false)
    private int percentComplete = 0;
}
