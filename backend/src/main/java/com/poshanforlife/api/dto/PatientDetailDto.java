package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.Gender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full patient view: account + medical profile + nested health records.
 * tempPassword is only present on the 201 create response when the password
 * was auto-generated (interim channel until the notification/email feature).
 * reports stays empty until the reports feature prompt lands.
 */
public record PatientDetailDto(
        String id,
        String name,
        String email,
        String phone,
        LocalDate dateOfBirth,
        boolean isActive,
        Gender gender,
        String bloodGroup,
        BigDecimal heightCm,
        String emergencyContact,
        String medicalHistory,
        String doctorNotes,
        List<DoctorRefDto> assignedDoctors,
        List<HealthRecordDto> healthRecords,
        List<Object> reports,
        String tempPassword,
        Instant createdAt,
        Instant updatedAt) {
}
