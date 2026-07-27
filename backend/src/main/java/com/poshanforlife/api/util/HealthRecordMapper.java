package com.poshanforlife.api.util;

import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.entity.HealthRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds {@link HealthRecordDto}s with per-field deltas vs. the immediately
 * previous record — shared by {@code PatientService} (the patient detail
 * view's embedded list) and {@code HealthRecordService} (the dedicated
 * history endpoint) so the delta math lives in exactly one place.
 */
public final class HealthRecordMapper {

    private HealthRecordMapper() {
    }

    /** The wire names accepted by the {@code fields} query param, one per metric. */
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "weight", "bodyFat", "bmi", "skeletalMuscleMass", "visceralFat", "bodyWater", "protein", "mineral", "bmr");

    /**
     * @param recordsAscending same patient's records ordered oldest-first — required for correct deltas.
     * @param heightCm         patient's height, for the BMI fallback on records with no stored bmi.
     * @param fields           when non-empty, restricted to {@link #KNOWN_FIELDS} names — every other
     *                         metric (and its delta) is nulled out in the returned DTOs.
     */
    public static List<HealthRecordDto> toDtos(List<HealthRecord> recordsAscending, BigDecimal heightCm, Set<String> fields) {
        List<HealthRecordDto> out = new ArrayList<>(recordsAscending.size());
        HealthRecord previous = null;
        for (HealthRecord hr : recordsAscending) {
            BigDecimal bmi = hr.getBmi() != null ? hr.getBmi() : computeBmi(hr.getWeightKg(), heightCm);
            BigDecimal previousBmi = previous == null ? null
                    : previous.getBmi() != null ? previous.getBmi() : computeBmi(previous.getWeightKg(), heightCm);

            HealthRecordDto dto = new HealthRecordDto(
                    hr.getId().toString(),
                    hr.getRecordDate(),
                    hr.getRecordedAt(),
                    hr.getSource(),
                    hr.getWeightKg(), delta(hr.getWeightKg(), previous == null ? null : previous.getWeightKg()),
                    hr.getBodyFatPct(), delta(hr.getBodyFatPct(), previous == null ? null : previous.getBodyFatPct()),
                    bmi, delta(bmi, previousBmi),
                    hr.getSkeletalMuscleMassKg(),
                    delta(hr.getSkeletalMuscleMassKg(), previous == null ? null : previous.getSkeletalMuscleMassKg()),
                    hr.getVisceralFatLevel(),
                    delta(hr.getVisceralFatLevel(), previous == null ? null : previous.getVisceralFatLevel()),
                    hr.getBodyWaterL(), delta(hr.getBodyWaterL(), previous == null ? null : previous.getBodyWaterL()),
                    hr.getProteinKg(), delta(hr.getProteinKg(), previous == null ? null : previous.getProteinKg()),
                    hr.getMineralKg(), delta(hr.getMineralKg(), previous == null ? null : previous.getMineralKg()),
                    hr.getBasalMetabolicRate(),
                    delta(hr.getBasalMetabolicRate(), previous == null ? null : previous.getBasalMetabolicRate()));

            out.add(fields.isEmpty() ? dto : restrict(dto, fields));
            previous = hr;
        }
        return out;
    }

    /** Nulls out every metric (and its delta) not named in {@code fields}; structural fields (id/date/source) are always kept. */
    public static HealthRecordDto restrict(HealthRecordDto d, Set<String> fields) {
        return new HealthRecordDto(
                d.id(), d.recordDate(), d.recordedAt(), d.source(),
                keep(fields, "weight", d.weightKg()), keep(fields, "weight", d.weightKgDelta()),
                keep(fields, "bodyFat", d.bodyFatPct()), keep(fields, "bodyFat", d.bodyFatPctDelta()),
                keep(fields, "bmi", d.bmi()), keep(fields, "bmi", d.bmiDelta()),
                keep(fields, "skeletalMuscleMass", d.skeletalMuscleMassKg()),
                keep(fields, "skeletalMuscleMass", d.skeletalMuscleMassKgDelta()),
                keep(fields, "visceralFat", d.visceralFatLevel()), keep(fields, "visceralFat", d.visceralFatLevelDelta()),
                keep(fields, "bodyWater", d.bodyWaterL()), keep(fields, "bodyWater", d.bodyWaterLDelta()),
                keep(fields, "protein", d.proteinKg()), keep(fields, "protein", d.proteinKgDelta()),
                keep(fields, "mineral", d.mineralKg()), keep(fields, "mineral", d.mineralKgDelta()),
                keep(fields, "bmr", d.basalMetabolicRate()), keep(fields, "bmr", d.basalMetabolicRateDelta()));
    }

    private static BigDecimal keep(Set<String> fields, String name, BigDecimal value) {
        return fields.contains(name) ? value : null;
    }

    private static BigDecimal delta(BigDecimal current, BigDecimal previous) {
        return current == null || previous == null ? null : current.subtract(previous);
    }

    /** BMI = kg / (m^2); null when either input is missing. */
    private static BigDecimal computeBmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }
}
