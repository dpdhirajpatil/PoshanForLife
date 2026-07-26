package com.poshanforlife.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * patientId is optional — a PATIENT caller always books for themselves (any
 * value they pass is ignored by the service); ADMIN/DOCTOR must supply it.
 */
public record CreateAppointmentRequest(
        UUID patientId,
        @NotNull UUID practitionerId,
        @NotNull @Future Instant scheduledAt,
        @Min(5) Integer durationMinutes,
        String notes) {
}
