package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.BadgeCriteriaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateBadgeRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 100) String iconKey,
        @NotNull BadgeCriteriaType criteriaType,
        @PositiveOrZero int criteriaValue) {
}
