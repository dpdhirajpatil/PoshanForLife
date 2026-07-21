package com.poshanforlife.api.dto;

import java.math.BigDecimal;

/**
 * totalPatients/activeDoctors/reportsThisMonth/pendingReviews/followupToday
 * are scoped to the caller (all-org for ADMIN, own patients/leads for
 * DOCTOR) — see DashboardService. myPatients is DOCTOR-only (the doctor's
 * own assigned-patient count); null for ADMIN, where it would be redundant
 * with totalPatients. avgInBodyScore is the average BMI across each visible
 * patient's most recent health record (same definition as
 * PatientStatsDto#averageBmi); null when no records exist yet.
 */
public record DashboardKpisDto(
        long totalPatients,
        long activeDoctors,
        long reportsThisMonth,
        BigDecimal avgInBodyScore,
        Long myPatients,
        long pendingReviews,
        long followupToday) {
}
