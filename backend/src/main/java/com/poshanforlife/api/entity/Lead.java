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

import java.time.Instant;

/**
 * A CRM lead/contact moving through the sales pipeline (NEW..CONVERTED/LOST).
 * convertedPatient links to the User created by the convert flow — null until
 * (and unless) this lead is converted. interestedProgramme is purely
 * informational (which catalogue programme they asked about); the actual
 * service assignment created on conversion is independent of it.
 */
@Getter
@Setter
@Entity
@Table(name = "leads")
public class Lead extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 32)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 100)
    private String city;

    private Integer age;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadSource source;

    @Column(name = "health_goal", columnDefinition = "text")
    private String healthGoal;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadStage stage = LeadStage.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_practitioner_id")
    private User assignedPractitioner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interested_programme_id")
    private Programme interestedProgramme;

    @Column(name = "next_followup_at")
    private Instant nextFollowupAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_patient_id")
    private User convertedPatient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
