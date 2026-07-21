package com.poshanforlife.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DOCTOR-only "needs attention" entry. Heuristic (see
 * DashboardService#computeAttentionPatients): a patient qualifies when their
 * most recent health record is 30+ days old, or they have no record at all —
 * reason is "no_recent_report" or "never_recorded" respectively.
 */
public record AttentionPatientDto(
        UUID patientId,
        String patientName,
        Instant lastRecordedAt,
        String reason) {
}
