package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("scheduled" | "completed" | "cancelled"). */
public enum AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AppointmentStatus fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
