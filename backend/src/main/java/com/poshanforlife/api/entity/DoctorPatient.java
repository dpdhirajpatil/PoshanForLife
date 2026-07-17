package com.poshanforlife.api.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Assignment of a patient to a doctor. The set of rows for a doctor is
 * replaced wholesale by POST /users/{id}/assign-patients.
 */
@Getter
@Setter
@Entity
@Table(name = "doctor_patients",
        uniqueConstraints = @UniqueConstraint(name = "uq_doctor_patient", columnNames = {"doctor_id", "patient_id"}))
public class DoctorPatient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;
}
