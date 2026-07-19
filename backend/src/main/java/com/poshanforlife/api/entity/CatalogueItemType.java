package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * The three catalogue resource groups. pathSegment is the URL segment
 * (/api/v1/catalogue/{pathSegment}) and the Supabase folder for cover images.
 * As a patient-programme serviceType the wire format is the lowercase label
 * ("programme" | "session" | "challenge").
 */
@Getter
@RequiredArgsConstructor
public enum CatalogueItemType {
    PROGRAMME("programmes", "programme"),
    SESSION("sessions", "session"),
    CHALLENGE("challenges", "challenge");

    private final String pathSegment;
    @JsonValue
    private final String label;

    public static CatalogueItemType fromPathSegment(String segment) {
        for (CatalogueItemType type : values()) {
            if (type.pathSegment.equals(segment)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown catalogue type: " + segment);
    }

    /** Case-insensitive parse of the wire label ("programme" | "session" | "challenge"). */
    @JsonCreator
    public static CatalogueItemType fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
