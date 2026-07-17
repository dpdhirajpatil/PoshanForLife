package com.poshanforlife.api.dto;

import java.math.BigDecimal;

/**
 * Aggregates over the caller's visible patients (all for ADMIN, assigned for
 * DOCTOR). activeThisMonth = active patients created or with a health record
 * in the current calendar month. Averages are computed from each patient's
 * latest health record; null when no data exists.
 */
public record PatientStatsDto(
        long totalPatients,
        long activeThisMonth,
        BigDecimal averageBmi,
        BigDecimal averageBodyFatPct) {
}
