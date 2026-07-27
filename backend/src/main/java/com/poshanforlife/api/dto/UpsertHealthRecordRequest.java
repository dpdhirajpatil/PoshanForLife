package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.HealthRecordSource;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for {@code POST /api/v1/health-records}. {@code patientId} is
 * required for ADMIN/DOCTOR and rejected for PATIENT/LEAD, who always write
 * their own record (see {@code HealthRecordService#resolveTargetPatientId}).
 * {@code source} is forced to MANUAL for ADMIN/DOCTOR regardless of what's
 * sent; PATIENT/LEAD must send {@code patient_manual} or {@code wearable_sync}.
 * {@code recordDate} defaults to today (UTC) when omitted. Every metric field
 * is optional and only overwrites the existing value on an upsert when
 * non-null, same as the InBody upload pipeline.
 */
public record UpsertHealthRecordRequest(
        String patientId,
        LocalDate recordDate,
        HealthRecordSource source,
        @DecimalMin(value = "0", inclusive = false) BigDecimal weightKg,
        @DecimalMin(value = "0", inclusive = false) BigDecimal bodyFatPct,
        BigDecimal skeletalMuscleMassKg,
        BigDecimal bmi,
        BigDecimal visceralFatLevel,
        BigDecimal bodyWaterL,
        BigDecimal proteinKg,
        BigDecimal mineralKg,
        BigDecimal basalMetabolicRate) {
}
