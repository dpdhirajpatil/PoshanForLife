package com.poshanforlife.api.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Like {@link AdminOrDoctorOrPatient}, plus LEAD — for endpoints a
 * self-signup LEAD account must also reach for its own data (e.g. health
 * records, before staff convert the Lead to a PATIENT). Scoping (own record
 * only, 404 not 403) is enforced in the service layer exactly as for PATIENT.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT', 'LEAD')")
public @interface AdminOrDoctorOrPatientOrLead {
}
