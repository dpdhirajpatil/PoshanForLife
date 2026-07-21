package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.LeadActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLeadActivityRequest(
        @NotNull LeadActivityType activityType,
        @NotBlank @Size(min = 1, max = 5000) String description) {
}
