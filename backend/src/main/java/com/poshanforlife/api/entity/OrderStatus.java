package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("active" | "completed" | "deactivated"). */
public enum OrderStatus {
    ACTIVE,
    COMPLETED,
    DEACTIVATED;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static OrderStatus fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
