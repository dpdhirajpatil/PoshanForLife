package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * password is optional — when absent a temporary password is generated and
 * returned once in the response. assignedDoctorId is ADMIN-only; a DOCTOR
 * caller is always auto-assigned to the new patient.
 */
public record CreatePatientRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Email String email,
        @Pattern(regexp = "^(?=.*\\d).{8,}$", message = "must be at least 8 characters and contain a digit")
        String password,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 8) String bloodGroup,
        @Positive BigDecimal heightCm,
        @Size(max = 255) String emergencyContact,
        String medicalHistory,
        UUID assignedDoctorId) {
}
