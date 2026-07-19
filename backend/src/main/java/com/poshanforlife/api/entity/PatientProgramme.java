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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A patient's assignment to a catalogue item. Exactly one of
 * programmeId/sessionId/challengeId is set, matching serviceType (enforced by
 * a DB CHECK constraint and the service layer). The catalogue columns are
 * plain UUIDs — no FK — because an ARCHIVED catalogue item may be deleted
 * while historical assignments still reference it (prompt 06 rule).
 *
 * priceInr is the price at assignment time (catalogue price or an override) —
 * it does not change if the catalogue item is repriced later.
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
    @Column(name = "service_type", nullable = false, length = 16)
    private CatalogueItemType serviceType;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "challenge_id")
    private UUID challengeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Derived from the catalogue item's duration; for sessions = startDate. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "price_inr", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceInr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PatientProgrammeStatus status = PatientProgrammeStatus.ACTIVE;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    /** Optional appointment-style tracking: which doctor delivers the service. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_doctor_id")
    private User assignedDoctor;

    /** The referenced catalogue item's id, whichever column is set. */
    public UUID itemId() {
        return programmeId != null ? programmeId : sessionId != null ? sessionId : challengeId;
    }
}
