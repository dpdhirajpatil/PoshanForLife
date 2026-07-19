package com.poshanforlife.api.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The three catalogue resource groups. pathSegment is the URL segment
 * (/api/v1/catalogue/{pathSegment}) and the Supabase folder for cover images.
 */
@Getter
@RequiredArgsConstructor
public enum CatalogueItemType {
    PROGRAMME("programmes", "programme"),
    SESSION("sessions", "session"),
    CHALLENGE("challenges", "challenge");

    private final String pathSegment;
    private final String label;

    public static CatalogueItemType fromPathSegment(String segment) {
        for (CatalogueItemType type : values()) {
            if (type.pathSegment.equals(segment)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown catalogue type: " + segment);
    }
}
