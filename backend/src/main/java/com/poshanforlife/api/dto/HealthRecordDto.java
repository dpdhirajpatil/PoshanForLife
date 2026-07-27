package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.HealthRecordSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One day's health snapshot. Each {@code *Delta} field is this record's value
 * minus the immediately-previous record's value for the same patient (null
 * when either side is missing, or there is no previous record) — computed
 * server-side by {@link com.poshanforlife.api.util.HealthRecordMapper} so
 * clients never have to. {@code bmi} is the stored value when present
 * (InBody uploads write it directly); otherwise it's recomputed from weight
 * and the patient's profile height.
 */
public record HealthRecordDto(
        String id,
        LocalDate recordDate,
        Instant recordedAt,
        HealthRecordSource source,
        BigDecimal weightKg,
        BigDecimal weightKgDelta,
        BigDecimal bodyFatPct,
        BigDecimal bodyFatPctDelta,
        BigDecimal bmi,
        BigDecimal bmiDelta,
        BigDecimal skeletalMuscleMassKg,
        BigDecimal skeletalMuscleMassKgDelta,
        BigDecimal visceralFatLevel,
        BigDecimal visceralFatLevelDelta,
        BigDecimal bodyWaterL,
        BigDecimal bodyWaterLDelta,
        BigDecimal proteinKg,
        BigDecimal proteinKgDelta,
        BigDecimal mineralKg,
        BigDecimal mineralKgDelta,
        BigDecimal basalMetabolicRate,
        BigDecimal basalMetabolicRateDelta) {
}
