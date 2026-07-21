package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.CatalogueItemType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * All contact/medical fields are overrides of the lead's own data — email is
 * the only one actually required, and only if the lead has none either
 * (LeadService rejects the conversion otherwise, since a patient account
 * needs an email). assignServiceId/serviceType/startDate/price are an
 * optional immediate service assignment, created via the same flow as
 * prompt 06's PatientProgrammeService — omit them to convert without one.
 */
public record ConvertLeadRequest(
        @Size(min = 2, max = 255) String name,
        @Size(max = 32) String phone,
        @Email @Size(max = 255) String email,
        LocalDate dateOfBirth,
        @Size(max = 8) String bloodGroup,
        @Positive BigDecimal heightCm,
        UUID assignServiceId,
        CatalogueItemType serviceType,
        LocalDate startDate,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
