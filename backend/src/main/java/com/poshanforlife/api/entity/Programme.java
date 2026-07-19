package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "programmes")
public class Programme extends CatalogueItem {

    @Column(name = "duration_weeks", nullable = false)
    private Integer durationWeeks;

    @Override
    public CatalogueItemType itemType() {
        return CatalogueItemType.PROGRAMME;
    }
}
