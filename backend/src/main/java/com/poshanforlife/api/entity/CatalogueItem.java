package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Shared shape of the three catalogue tables (programmes, sessions,
 * challenges). serviceCode is globally unique across ALL three tables —
 * enforced via the catalogue_service_codes registry, not per-table.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class CatalogueItem extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "service_code", nullable = false, length = 64)
    private String serviceCode;

    /** Free-text sub-category (e.g. "Weight loss", "Yoga"). */
    @Column(nullable = false, length = 100)
    private String type;

    @Column(name = "price_inr", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceInr;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CatalogueStatus status = CatalogueStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public abstract CatalogueItemType itemType();
}
