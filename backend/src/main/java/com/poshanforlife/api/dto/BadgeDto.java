package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.BadgeCriteriaType;

import java.time.Instant;

public record BadgeDto(
        String id,
        String name,
        String description,
        String iconKey,
        BadgeCriteriaType criteriaType,
        int criteriaValue,
        Instant createdAt) {
}
