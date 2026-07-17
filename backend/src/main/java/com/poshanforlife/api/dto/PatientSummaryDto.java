package com.poshanforlife.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Row shape for the patients table. Age is derived client-side from dateOfBirth. */
public record PatientSummaryDto(
        String id,
        String name,
        String email,
        String phone,
        LocalDate dateOfBirth,
        boolean isActive,
        List<DoctorRefDto> assignedDoctors,
        Instant createdAt) {
}
