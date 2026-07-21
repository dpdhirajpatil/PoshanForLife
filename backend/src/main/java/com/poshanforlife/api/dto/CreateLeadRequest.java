package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Gender;
import com.poshanforlife.api.entity.LeadSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * name and source are required; everything else is optional.
 * assignedPractitionerId is ADMIN-only — a DOCTOR caller is always
 * auto-assigned to the new lead (LeadService rejects an explicit value from
 * a DOCTOR).
 */
public record CreateLeadRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @Size(max = 32) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 100) String city,
        @Min(0) @Max(150) Integer age,
        Gender gender,
        @NotNull LeadSource source,
        String healthGoal,
        @Size(max = 5000) String notes,
        UUID assignedPractitionerId,
        UUID interestedProgrammeId,
        Instant nextFollowupAt) {
}
