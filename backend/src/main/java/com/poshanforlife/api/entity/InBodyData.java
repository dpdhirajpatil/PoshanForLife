package com.poshanforlife.api.entity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured fields Claude is asked to extract from an InBody PDF, persisted
 * as a jsonb column on {@link Report#getParsedData()}. Field list is a
 * scaffold of typical InBody-report metrics (body composition, water,
 * mineral/protein, metabolic, and target/control figures) — extend here if a
 * real report surfaces a field this doesn't cover yet. Every field is
 * nullable: Claude returns null (not a guess) for anything it can't find.
 */
public record InBodyData(
        BigDecimal weightKg,
        BigDecimal bodyFatPercent,
        BigDecimal skeletalMuscleMassKg,
        BigDecimal bmi,
        BigDecimal visceralFatLevel,
        BigDecimal bodyWaterL,
        BigDecimal proteinKg,
        BigDecimal mineralKg,
        BigDecimal basalMetabolicRate,
        BigDecimal bodyFatMassKg,
        BigDecimal fatFreeMassKg,
        BigDecimal waistHipRatio,
        BigDecimal targetWeightKg,
        BigDecimal weightControlKg,
        BigDecimal fatControlKg,
        BigDecimal muscleControlKg,
        BigDecimal obesityDegreePercent,
        BigDecimal intracellularWaterL,
        BigDecimal extracellularWaterL,
        Integer inbodyScore) {

    private static final List<String> FIELD_ORDER = List.of(
            "weightKg", "bodyFatPercent", "skeletalMuscleMassKg", "bmi", "visceralFatLevel",
            "bodyWaterL", "proteinKg", "mineralKg", "basalMetabolicRate", "bodyFatMassKg",
            "fatFreeMassKg", "waistHipRatio", "targetWeightKg", "weightControlKg", "fatControlKg",
            "muscleControlKg", "obesityDegreePercent", "intracellularWaterL", "extracellularWaterL",
            "inbodyScore");

    public static int totalFieldCount() {
        return FIELD_ORDER.size();
    }

    /** How many of the 20 fields Claude actually populated (non-null). */
    public int extractedFieldCount() {
        int count = 0;
        Object[] values = {weightKg, bodyFatPercent, skeletalMuscleMassKg, bmi, visceralFatLevel,
                bodyWaterL, proteinKg, mineralKg, basalMetabolicRate, bodyFatMassKg, fatFreeMassKg,
                waistHipRatio, targetWeightKg, weightControlKg, fatControlKg, muscleControlKg,
                obesityDegreePercent, intracellularWaterL, extracellularWaterL, inbodyScore};
        for (Object value : values) {
            if (value != null) count++;
        }
        return count;
    }
}
