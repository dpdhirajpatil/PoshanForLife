package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.CatalogueStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One DTO for all three catalogue types — the type-specific duration/goal
 * fields are null (and omitted from JSON) for the other types.
 * activeAssignmentCount lets the frontend disable Delete up front.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogueItemDto(
        String id,
        String name,
        String serviceCode,
        String type,
        BigDecimal priceInr,
        String description,
        String coverImageUrl,
        CatalogueStatus status,
        Integer durationWeeks,
        Integer durationMinutes,
        Integer durationDays,
        String goalDescription,
        long activeAssignmentCount,
        UserRefDto createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
