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

/**
 * One entry in a lead's activity timeline. oldStage/newStage are set only for
 * activityType=STAGE_CHANGE (auto-created by LeadService when a PATCH moves
 * the lead's stage) — null for manually logged activities.
 */
@Getter
@Setter
@Entity
@Table(name = "lead_activities")
public class LeadActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 16)
    private LeadActivityType activityType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_stage", length = 16)
    private LeadStage oldStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_stage", length = 16)
    private LeadStage newStage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
