package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.DocumentStatus;
import com.poshanforlife.api.entity.DocumentType;

import java.math.BigDecimal;
import java.time.Instant;

public record DocumentListItemDto(
        String id,
        DocumentType documentType,
        String documentNumber,
        DocumentStatus status,
        LeadRefDto lead,
        PatientRefDto patient,
        BigDecimal total,
        Instant createdAt) {
}
