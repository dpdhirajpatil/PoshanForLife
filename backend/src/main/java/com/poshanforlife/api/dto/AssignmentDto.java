package com.poshanforlife.api.dto;

import java.time.Instant;

/** One doctor–patient assignment with names populated for display. */
public record AssignmentDto(
        String id,
        DoctorRefDto doctor,
        DoctorRefDto patient,
        Instant assignedAt) {
}
