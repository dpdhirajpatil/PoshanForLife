package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.DocumentStatus;
import com.poshanforlife.api.entity.DocumentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Full detail with computed totals — the native mobile detail screen renders this directly, no PDF viewer needed. */
public record DocumentDetailDto(
        String id,
        DocumentType documentType,
        String documentNumber,
        DocumentStatus status,
        LeadRefDto lead,
        PatientRefDto patient,
        List<DocumentItemDto> items,
        BigDecimal discountInr,
        BigDecimal subtotal,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal total,
        String notes,
        Integer validForDays,
        UserRefDto createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
