package com.poshanforlife.api.dto;

import jakarta.validation.constraints.Size;

/**
 * POST /leads/me/request-consultation body — a self-signed-up LEAD asking to
 * be contacted. Both fields are optional; appended as a NOTE activity on the
 * caller's own Lead record (see LeadService.requestConsultation).
 */
public record RequestConsultationRequest(
        @Size(max = 100) String preferredContactTime,
        @Size(max = 2000) String message) {
}
