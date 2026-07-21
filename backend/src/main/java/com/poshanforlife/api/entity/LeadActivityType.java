package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Wire format lowercase ("note" | "call" | "whatsapp" | "estimate_sent" |
 * "stage_change" | "converted"). STAGE_CHANGE and CONVERTED are created
 * automatically by the service layer (PATCH stage changes / the convert
 * flow) — the "Log activity" quick-add only offers the other four.
 */
public enum LeadActivityType {
    NOTE,
    CALL,
    WHATSAPP,
    ESTIMATE_SENT,
    STAGE_CHANGE,
    CONVERTED;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static LeadActivityType fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
