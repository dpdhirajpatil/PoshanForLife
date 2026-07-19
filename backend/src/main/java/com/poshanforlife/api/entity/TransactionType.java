package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("activation" | "deactivation" | "refund"). */
public enum TransactionType {
    ACTIVATION,
    DEACTIVATION,
    REFUND;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static TransactionType fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
