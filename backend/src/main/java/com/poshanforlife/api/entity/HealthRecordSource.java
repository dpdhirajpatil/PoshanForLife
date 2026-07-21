package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("manual" | "inbody_upload"). */
public enum HealthRecordSource {
    MANUAL,
    INBODY_UPLOAD;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static HealthRecordSource fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
