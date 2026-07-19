package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "challenges")
public class Challenge extends CatalogueItem {

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "goal_description", columnDefinition = "text", nullable = false)
    private String goalDescription;

    @Override
    public CatalogueItemType itemType() {
        return CatalogueItemType.CHALLENGE;
    }
}
