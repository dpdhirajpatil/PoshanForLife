package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.CatalogueStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Shared create payload for all three catalogue types. Which duration/goal
 * field is required depends on the URL's type and is enforced in
 * CatalogueService (bean validation only covers the shared shape).
 * status defaults to draft when omitted.
 */
public record CreateCatalogueItemRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Size(max = 64) String serviceCode,
        @NotBlank @Size(max = 100) String type,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal priceInr,
        String description,
        @Size(max = 1000) String coverImageUrl,
        CatalogueStatus status,
        @Positive Integer durationWeeks,
        @Positive Integer durationMinutes,
        @Positive Integer durationDays,
        String goalDescription) {
}
