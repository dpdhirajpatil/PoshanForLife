package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Wire format lowercase ("manual" | "inbody_upload" | "patient_manual" |
 * "wearable_sync"). PATIENT_MANUAL/WEARABLE_SYNC are written exclusively by
 * a PATIENT/LEAD caller logging their own data (mobile manual-entry screen
 * vs. Health Connect sync); MANUAL/INBODY_UPLOAD stay staff-only, as before.
 */
public enum HealthRecordSource {
    MANUAL,
    INBODY_UPLOAD,
    PATIENT_MANUAL,
    WEARABLE_SYNC;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static HealthRecordSource fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
