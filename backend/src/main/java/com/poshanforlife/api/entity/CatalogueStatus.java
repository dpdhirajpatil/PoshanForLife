package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Catalogue item lifecycle. Wire format is lowercase
 * ("draft" | "published" | "archived") per the original API contract;
 * the DB stores the uppercase enum name.
 */
public enum CatalogueStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive parse; throws IllegalArgumentException on unknown values. */
    @JsonCreator
    public static CatalogueStatus fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
