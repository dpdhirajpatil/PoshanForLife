package com.poshanforlife.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** bmi is computed from the record's weight and the patient's profile height. */
public record HealthRecordDto(
        String id,
        BigDecimal weightKg,
        BigDecimal bodyFatPct,
        BigDecimal bmi,
        Instant recordedAt) {
}
