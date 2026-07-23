package com.poshanforlife.api.entity;

/**
 * User roles. PATIENT exists in the data model even though this portal
 * has no patient-facing UI (patients use the separate mobile app). LEAD is
 * a real, authenticated account created by mobile self-signup (POST
 * /auth/signup) — minimal permissions (own profile + own notifications
 * only), promoted in place to PATIENT when staff convert their linked Lead.
 */
public enum Role {
    ADMIN,
    DOCTOR,
    PATIENT,
    LEAD
}
