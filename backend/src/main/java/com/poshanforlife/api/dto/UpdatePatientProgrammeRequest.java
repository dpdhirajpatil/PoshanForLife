package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.PatientProgrammeStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial update — status, dates and notes only. The underlying service
 * reference is immutable by design: assigning a different catalogue item is a
 * new assignment, not an edit. notes accepts "" to clear.
 */
public record UpdatePatientProgrammeRequest(
        PatientProgrammeStatus status,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 5000) String notes) {
}
