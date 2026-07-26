package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Gender;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** PATCH body — all optional; absent (null) fields are left unchanged. */
public record UpdatePatientRequest(
        @Size(min = 2, max = 255) String name,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 8) String bloodGroup,
        @Positive BigDecimal heightCm,
        @Size(max = 255) String emergencyContact,
        String medicalHistory,
        /** Practitioner-only clinical notes — UI label is "Notes"/"Practitioner notes", wire/DB name stays doctorNotes. */
        String doctorNotes) {
}
