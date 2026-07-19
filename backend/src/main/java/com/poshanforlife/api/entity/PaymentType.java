package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("offline" | "online" | "credit"). */
public enum PaymentType {
    OFFLINE,
    ONLINE,
    CREDIT;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PaymentType fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
