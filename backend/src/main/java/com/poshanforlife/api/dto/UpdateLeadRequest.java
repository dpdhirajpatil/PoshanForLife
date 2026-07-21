package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Gender;
import com.poshanforlife.api.entity.LeadStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** All fields optional — only non-null ones are applied. */
public record UpdateLeadRequest(
        @Size(min = 2, max = 255) String name,
        @Size(max = 32) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 100) String city,
        @Min(0) @Max(150) Integer age,
        Gender gender,
        String healthGoal,
        @Size(max = 5000) String notes,
        LeadStage stage,
        UUID assignedPractitionerId,
        UUID interestedProgrammeId,
        Instant nextFollowupAt) {
}
