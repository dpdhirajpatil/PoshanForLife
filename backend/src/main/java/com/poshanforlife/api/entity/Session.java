package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sessions")
public class Session extends CatalogueItem {

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Override
    public CatalogueItemType itemType() {
        return CatalogueItemType.SESSION;
    }
}
