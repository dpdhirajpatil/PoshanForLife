package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.AppointmentStatus;
import jakarta.validation.constraints.Future;

import java.time.Instant;

/**
 * scheduledAt present = reschedule (also resets a cancelled appointment back
 * to SCHEDULED unless status is set explicitly in the same request); status
 * present = cancel/complete; notes present = practitioner's post-appointment
 * notes. All fields optional, applied independently.
 */
public record UpdateAppointmentRequest(
        @Future Instant scheduledAt,
        AppointmentStatus status,
        String notes) {
}
