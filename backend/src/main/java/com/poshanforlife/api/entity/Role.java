package com.poshanforlife.api.entity;

/**
 * User roles. PATIENT exists in the data model even though this portal
 * has no patient-facing UI (patients use the separate mobile app).
 */
public enum Role {
    ADMIN,
    DOCTOR,
    PATIENT
}
