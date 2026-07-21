package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.Gender;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadListItemDto(
        String id,
        String name,
        String phone,
        String email,
        String city,
        Integer age,
        Gender gender,
        LeadSource source,
        LeadStage stage,
        UserRefDto assignedPractitioner,
        ServiceRefDto interestedProgramme,
        Instant nextFollowupAt,
        Instant createdAt) {
}
