package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.CatalogueStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial update — null fields are left unchanged. description and
 * coverImageUrl accept "" to clear the stored value.
 */
public record UpdateCatalogueItemRequest(
        @Size(min = 2, max = 255) String name,
        @Size(min = 1, max = 64) String serviceCode,
        @Size(min = 1, max = 100) String type,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal priceInr,
        String description,
        @Size(max = 1000) String coverImageUrl,
        CatalogueStatus status,
        @Positive Integer durationWeeks,
        @Positive Integer durationMinutes,
        @Positive Integer durationDays,
        String goalDescription) {
}
