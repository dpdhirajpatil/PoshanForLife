package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("inbody" | "lab" | "prescription" | "other"). */
public enum ReportType {
    INBODY,
    LAB,
    PRESCRIPTION,
    OTHER;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ReportType fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
