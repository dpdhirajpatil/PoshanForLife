package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.LeadActivityType;
import com.poshanforlife.api.entity.LeadStage;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadActivityDto(
        String id,
        LeadActivityType activityType,
        String description,
        LeadStage oldStage,
        LeadStage newStage,
        UserRefDto createdBy,
        Instant createdAt) {
}
