package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.PatientProgrammeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A service assignment with its commercial context. catalogueItem is null
 * when the (archived) catalogue item was deleted after assignment; endDate is
 * the startDate itself for sessions (single-day appointment).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatientProgrammeDto(
        String id,
        CatalogueItemType serviceType,
        ServiceRefDto catalogueItem,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal priceInr,
        PatientProgrammeStatus status,
        String notes,
        UserRefDto assignedBy,
        UserRefDto assignedDoctor,
        OrderSummaryDto order,
        Instant createdAt,
        Instant updatedAt) {
}
