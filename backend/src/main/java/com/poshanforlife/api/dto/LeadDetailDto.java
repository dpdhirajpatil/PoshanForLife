package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.Gender;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadDetailDto(
        String id,
        String name,
        String phone,
        String email,
        String city,
        Integer age,
        Gender gender,
        LeadSource source,
        String healthGoal,
        String notes,
        LeadStage stage,
        UserRefDto assignedPractitioner,
        ServiceRefDto interestedProgramme,
        Instant nextFollowupAt,
        String convertedPatientId,
        UserRefDto createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<LeadActivityDto> activities) {
}
