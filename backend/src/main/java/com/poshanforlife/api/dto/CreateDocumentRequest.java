package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Exactly one of leadId/patientId must be set (service-layer 422, mirrored by
 * the DB CHECK constraint) — a DOCTOR caller must own that lead/patient.
 * validForDays only applies to (and is only accepted for) documentType=estimate.
 */
public record CreateDocumentRequest(
        @NotNull DocumentType documentType,
        UUID leadId,
        UUID patientId,
        @NotEmpty @Valid List<CreateDocumentItemRequest> items,
        @Size(max = 5000) String notes,
        @PositiveOrZero BigDecimal discountInr,
        Integer validForDays) {
}
