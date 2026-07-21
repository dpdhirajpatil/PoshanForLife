package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Wire format lowercase ("new" | "contacted" | "qualified" | "proposed" |
 * "converted" | "lost"). Pipeline order matches the declaration order above.
 */
public enum LeadStage {
    NEW,
    CONTACTED,
    QUALIFIED,
    PROPOSED,
    CONVERTED,
    LOST;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static LeadStage fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
