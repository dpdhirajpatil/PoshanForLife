package com.poshanforlife.api.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint available to both staff roles. DOCTOR data scoping (own patients/
 * leads/transactions only) is enforced in the service layer per feature,
 * using the {@code AuthenticatedUser} principal's id.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
public @interface AdminOrDoctor {
}
