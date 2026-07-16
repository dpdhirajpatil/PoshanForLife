package com.poshanforlife.api.security;

import com.poshanforlife.api.entity.Role;

/** The authenticated principal placed in the SecurityContext by {@link JwtAuthenticationFilter}. */
public record AuthenticatedUser(String id, String email, Role role) {
}
