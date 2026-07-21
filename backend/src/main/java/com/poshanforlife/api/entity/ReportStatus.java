package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("pending" | "processing" | "done" | "error"). */
public enum ReportStatus {
    PENDING,
    PROCESSING,
    DONE,
    ERROR;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ReportStatus fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
