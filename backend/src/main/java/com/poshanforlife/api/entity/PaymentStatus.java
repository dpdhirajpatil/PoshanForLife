package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("paid" | "unpaid" | "pending"). */
public enum PaymentStatus {
    PAID,
    UNPAID,
    PENDING;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentStatus fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
