package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("draft" | "sent" | "paid"). */
public enum DocumentStatus {
    DRAFT,
    SENT,
    PAID;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static DocumentStatus fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
