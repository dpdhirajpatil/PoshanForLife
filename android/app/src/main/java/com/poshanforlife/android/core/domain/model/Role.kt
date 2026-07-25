package com.poshanforlife.android.core.domain.model

/**
 * Mirrors the backend's Role enum (ADMIN, DOCTOR, PATIENT, LEAD). DOCTOR's
 * UI-facing label is "Practitioner" everywhere in this app — only the wire
 * value stays DOCTOR, to match the backend contract.
 */
enum class Role {
    ADMIN,
    DOCTOR,
    PATIENT,
    LEAD,
    /** Unrecognized wire value (backend enum drift) — treated as logged out, never crashes. */
    UNKNOWN,
    ;

    companion object {
        fun fromWire(value: String?): Role = entries.find { it.name == value } ?: UNKNOWN
    }
}
