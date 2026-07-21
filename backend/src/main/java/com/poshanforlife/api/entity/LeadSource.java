package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Wire format lowercase ("referral" | "social_media" | "website" | "walk_in"
 * | "phone" | "whatsapp" | "mobile_app" | "other").
 */
public enum LeadSource {
    REFERRAL,
    SOCIAL_MEDIA,
    WEBSITE,
    WALK_IN,
    PHONE,
    WHATSAPP,
    MOBILE_APP,
    OTHER;

    @JsonValue
    public String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static LeadSource fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
