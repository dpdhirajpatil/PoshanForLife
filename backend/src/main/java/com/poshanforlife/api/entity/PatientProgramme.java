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

import java.util.UUID;

/**
 * A patient's assignment to a catalogue item (any of the three types —
 * itemType discriminates which table itemId points into). Minimal for now:
 * the catalogue feature only needs it to block deleting items that are
 * actively assigned; the orders prompt extends it later.
 */
@Getter
@Setter
@Entity
@Table(name = "patient_programmes")
public class PatientProgramme extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private CatalogueItemType itemType;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PatientProgrammeStatus status = PatientProgrammeStatus.ACTIVE;
}
