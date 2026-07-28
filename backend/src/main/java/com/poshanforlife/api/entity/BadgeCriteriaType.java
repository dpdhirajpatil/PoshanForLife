package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Wire format lowercase ("challenge_completed" | "programme_count" | "streak_days" | "custom"). */
public enum BadgeCriteriaType {
    CHALLENGE_COMPLETED,
    PROGRAMME_COUNT,
    STREAK_DAYS,
    CUSTOM;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static BadgeCriteriaType fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
