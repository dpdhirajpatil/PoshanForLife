package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An admin-manageable product category (e.g. "Meal Replacements"), not a
 * fixed enum — the brand's product lineup keeps growing. displayOrder
 * controls tab/section order on the browse page.
 */
@Getter
@Setter
@Entity
@Table(name = "product_segments")
public class ProductSegment extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SegmentStatus status = SegmentStatus.ACTIVE;
}
