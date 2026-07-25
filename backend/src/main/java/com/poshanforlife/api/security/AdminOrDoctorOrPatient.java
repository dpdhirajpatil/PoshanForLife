package com.poshanforlife.api.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Read endpoint available to both staff roles and a PATIENT reading their
 * own data. PATIENT data scoping (own record only, everything else is a 404
 * not a 403 — see the reports/health-records services) is enforced in the
 * service layer, using the {@code AuthenticatedUser} principal's id, the
 * same way DOCTOR scoping already works.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
public @interface AdminOrDoctorOrPatient {
}
