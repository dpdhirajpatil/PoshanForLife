package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.BadgeCriteriaType;

import java.time.Instant;

/** The full badge catalog annotated per-patient — includes locked/unearned badges so the client can render them grayed-out. */
public record PatientBadgeStatusDto(
        String id,
        String name,
        String description,
        String iconKey,
        BadgeCriteriaType criteriaType,
        int criteriaValue,
        boolean earned,
        Instant earnedAt) {
}
