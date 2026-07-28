package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An admin-managed badge definition — a small, mostly-static reference
 * table. iconKey identifies which icon/asset the client renders; no binary
 * asset is stored here. criteriaValue's meaning depends on criteriaType
 * (e.g. "3" for programme_count means "complete 3 programmes"); custom
 * badges have no automatic evaluation logic and criteriaValue is unused
 * for them.
 */
@Getter
@Setter
@Entity
@Table(name = "badges")
public class Badge extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "criteria_type", nullable = false, length = 32)
    private BadgeCriteriaType criteriaType;

    @Column(name = "criteria_value", nullable = false)
    private int criteriaValue;
}
