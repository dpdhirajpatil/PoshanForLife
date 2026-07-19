package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Registry giving every catalogue item's serviceCode global uniqueness across
 * programmes/sessions/challenges: one row per item, with a case-insensitive
 * unique index on code. Maintained by CatalogueService in the same
 * transaction as the item itself.
 */
@Getter
@Setter
@Entity
@Table(name = "catalogue_service_codes")
public class CatalogueServiceCode extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private CatalogueItemType itemType;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;
}
