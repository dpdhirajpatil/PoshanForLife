package com.poshanforlife.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the merged recent-activity feed. type is one of
 * "patient_created" | "report_created" | "lead_stage_change". patientId/leadId
 * are set depending on type, letting the frontend deep-link the entry.
 */
public record DashboardActivityDto(
        String type,
        String message,
        Instant occurredAt,
        UUID patientId,
        UUID leadId) {
}
