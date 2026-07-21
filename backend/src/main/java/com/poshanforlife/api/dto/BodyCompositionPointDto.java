package com.poshanforlife.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One scatter point: x = body fat %, y = skeletal muscle mass (kg), from a patient's latest record. */
public record BodyCompositionPointDto(
        UUID patientId,
        String patientName,
        BigDecimal x,
        BigDecimal y) {
}
