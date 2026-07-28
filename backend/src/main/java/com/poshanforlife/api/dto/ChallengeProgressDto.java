package com.poshanforlife.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ChallengeProgressDto(
        String id,
        String patientProgrammeId,
        int currentStreak,
        int longestStreak,
        LocalDate lastLoggedDate,
        int percentComplete,
        Instant updatedAt) {
}
