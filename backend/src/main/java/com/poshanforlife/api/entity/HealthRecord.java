package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A patient's health snapshot for one calendar day (unique per patient+day —
 * see prompt 10). Originally weight/body-fat only; extended here (prompt 9)
 * with the InBody-derived metrics the Reports upload pipeline writes on a
 * successful extraction, identified by {@code source=INBODY_UPLOAD}.
 */
@Getter
@Setter
@Entity
@Table(name = "health_records")
public class HealthRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    private BigDecimal bodyFatPct;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Calendar day this snapshot belongs to — unique per patient. */
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "skeletal_muscle_mass_kg", precision = 5, scale = 1)
    private BigDecimal skeletalMuscleMassKg;

    /** Stored directly for InBody-sourced records (no height-based recompute needed). */
    @Column(precision = 4, scale = 1)
    private BigDecimal bmi;

    @Column(name = "visceral_fat_level", precision = 4, scale = 1)
    private BigDecimal visceralFatLevel;

    @Column(name = "body_water_l", precision = 5, scale = 1)
    private BigDecimal bodyWaterL;

    @Column(name = "protein_kg", precision = 4, scale = 1)
    private BigDecimal proteinKg;

    @Column(name = "mineral_kg", precision = 4, scale = 2)
    private BigDecimal mineralKg;

    @Column(name = "basal_metabolic_rate", precision = 6, scale = 1)
    private BigDecimal basalMetabolicRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HealthRecordSource source = HealthRecordSource.MANUAL;
}
