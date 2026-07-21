package com.poshanforlife.api.dto;

import java.util.UUID;

/** ADMIN-only assignment overview row: one active doctor + their assigned-patient count. */
public record DoctorPatientCountDto(UUID doctorId, String doctorName, long patientCount) {
}
