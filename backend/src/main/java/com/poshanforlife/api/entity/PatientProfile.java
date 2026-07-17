package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Medical profile, 1:1 with a User of role PATIENT. dateOfBirth intentionally
 * stays on User (added in V3). Patients created before this feature may not
 * have a profile row yet — it is created lazily on first medical-info update.
 */
@Getter
@Setter
@Entity
@Table(name = "patient_profiles")
public class PatientProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Gender gender;

    @Column(name = "blood_group", length = 8)
    private String bloodGroup;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "emergency_contact")
    private String emergencyContact;

    @Column(name = "medical_history", columnDefinition = "text")
    private String medicalHistory;

    @Column(name = "doctor_notes", columnDefinition = "text")
    private String doctorNotes;
}
