package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.CatalogueItemType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Assign a catalogue service to a patient. Exactly the id matching
 * serviceType must be set (enforced in the service layer). startDate defaults
 * to today; priceInr overrides the catalogue price when present.
 * assignedDoctorId is optional; DOCTOR callers are always recorded as the
 * assigned doctor themselves.
 */
public record CreatePatientProgrammeRequest(
        @NotNull CatalogueItemType serviceType,
        UUID programmeId,
        UUID sessionId,
        UUID challengeId,
        LocalDate startDate,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal priceInr,
        @Size(max = 5000) String notes,
        UUID assignedDoctorId) {
}
