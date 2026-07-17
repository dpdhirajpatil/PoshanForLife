package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Minimal health record — weight and body-fat snapshot per patient. Owned and
 * extended by the health-records feature prompt (InBody metrics etc.); created
 * here so patient stats (avg BMI / body fat) are computable.
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
}
