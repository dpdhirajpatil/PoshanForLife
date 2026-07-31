package com.poshanforlife.api.dto;

import java.time.LocalDate;

/**
 * percentComplete treats a {@value #RING_TARGET_DAYS}-day streak as "full"
 * for the progress ring — an arbitrary but round target (matches the
 * "logged 7 days in a row" badge example) since a Lead has no fixed-duration
 * challenge to measure percentComplete against like a real PatientProgramme does.
 */
public record LeadStreakDto(int currentStreak, int longestStreak, LocalDate lastLoggedDate, int percentComplete) {
    public static final int RING_TARGET_DAYS = 7;
}
