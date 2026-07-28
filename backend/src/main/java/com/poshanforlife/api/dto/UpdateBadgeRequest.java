package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.BadgeCriteriaType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Partial update — every field optional, only non-null ones are applied. */
public record UpdateBadgeRequest(
        @Size(min = 2, max = 255) String name,
        @Size(max = 2000) String description,
        @Size(max = 100) String iconKey,
        BadgeCriteriaType criteriaType,
        @PositiveOrZero Integer criteriaValue) {
}
